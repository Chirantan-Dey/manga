package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.lingala.zip4j.ZipFile
import suwayomi.tachidesk.graphql.types.AboutWebUI
import suwayomi.tachidesk.graphql.types.UpdateState
import suwayomi.tachidesk.graphql.types.UpdateState.DOWNLOADING
import suwayomi.tachidesk.graphql.types.UpdateState.ERROR
import suwayomi.tachidesk.graphql.types.UpdateState.FINISHED
import suwayomi.tachidesk.graphql.types.UpdateState.IDLE
import suwayomi.tachidesk.graphql.types.WebUIChannel
import suwayomi.tachidesk.graphql.types.WebUIFlavor
import suwayomi.tachidesk.graphql.types.WebUIUpdateInfo
import suwayomi.tachidesk.graphql.types.WebUIUpdateStatus
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.ExitCode.WebUISetupFailure
import suwayomi.tachidesk.util.HAScheduler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Date
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val applicationDirs: ApplicationDirs by injectLazy()
private val tmpDir = System.getProperty("java.io.tmpdir")

private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

class BundledWebUIMissing : Exception("No bundled webUI version found")

fun WebUIFlavor.isDefault(): Boolean = this == WebUIFlavor.default

object WebInterfaceManager {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private const val LAST_WEBUI_UPDATE_CHECK_KEY = "lastWebUIUpdateCheck"
    private const val SERVED_WEBUI_FLAVOR_KEY = "servedWebUIFlavor"

    private val preferences = Injekt.get<Application>().getSharedPreferences("server_util", Context.MODE_PRIVATE)
    private var currentUpdateTaskId: String = ""

    private val json: Json by injectLazy()
    private val network: NetworkHelper by injectLazy()

    private val notifyFlow =
        MutableSharedFlow<WebUIUpdateStatus>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

    private val statusFlow = MutableSharedFlow<WebUIUpdateStatus>()
    val status =
        statusFlow.stateIn(
            scope,
            SharingStarted.Eagerly,
            getStatus(),
        )

    init {
        scope.launch {
            @OptIn(FlowPreview::class)
            notifyFlow.sample(1.seconds).collect {
                statusFlow.emit(it)
            }
        }

        serverConfig.subscribeTo(
            combine(serverConfig.webUIUpdateCheckInterval, serverConfig.webUIFlavor) { interval, flavor ->
                Pair(
                    interval,
                    flavor,
                )
            },
            ::scheduleWebUIUpdateCheck,
            ignoreInitialValue = false,
        )
    }

    fun getAboutInfo(): AboutWebUI {
        val currentVersion = getLocalVersion()

        val failedToGetVersion = currentVersion === "r-1"
        if (failedToGetVersion) {
            throw Exception("Failed to get current version")
        }

        return AboutWebUI(
            channel = serverConfig.webUIChannel.value,
            tag = currentVersion,
        )
    }

    fun getStatus(
        version: String = "",
        state: UpdateState = IDLE,
        progress: Int = 0,
    ): WebUIUpdateStatus =
        WebUIUpdateStatus(
            info =
                WebUIUpdateInfo(
                    channel = serverConfig.webUIChannel.value,
                    tag = version,
                ),
            state,
            progress,
        )

    fun resetStatus() {
        emitStatus("", IDLE, 0, immediate = true)
    }

    private var serveWebUI: () -> Unit = {}

    fun setServeWebUI(serveWebUI: () -> Unit) {
        this.serveWebUI = serveWebUI
    }

    private fun setServedWebUIFlavor(flavor: WebUIFlavor) {
        preferences.edit().putString(SERVED_WEBUI_FLAVOR_KEY, flavor.uiName).apply()
    }

    private fun getServedWebUIFlavor(): WebUIFlavor =
        WebUIFlavor.from(preferences.getString(SERVED_WEBUI_FLAVOR_KEY, WebUIFlavor.default.uiName)!!)

    private fun isAutoUpdateEnabled(): Boolean = serverConfig.webUIUpdateCheckInterval.value.toInt() > 0

    @OptIn(DelicateCoroutinesApi::class)
    private fun scheduleWebUIUpdateCheck() {
        HAScheduler.descheduleCron(currentUpdateTaskId)

        val isAutoUpdateDisabled = !isAutoUpdateEnabled() || serverConfig.webUIFlavor.value == WebUIFlavor.CUSTOM
        if (isAutoUpdateDisabled) {
            return
        }

        val updateInterval =
            serverConfig.webUIUpdateCheckInterval.value.hours
                .coerceAtLeast(1.hours)
                .coerceAtMost(23.hours)
        val lastAutomatedUpdate = preferences.getLong(LAST_WEBUI_UPDATE_CHECK_KEY, System.currentTimeMillis())

        val task = {
            val log =
                KotlinLogging.logger(
                    "${logger.name}::scheduleWebUIUpdateCheck(" +
                        "flavor= ${WebUIFlavor.current.uiName}, " +
                        "channel= ${serverConfig.webUIChannel.value}, " +
                        "interval= ${serverConfig.webUIUpdateCheckInterval.value}h, " +
                        "lastAutomatedUpdate= ${
                            Date(
                                lastAutomatedUpdate,
                            )
                        })",
                )
            log.debug { "called" }

            runBlocking {
                try {
                    checkForUpdate(WebUIFlavor.current)
                } catch (e: Exception) {
                    log.error(e) { "failed due to" }
                }
            }
        }

        val wasPreviousUpdateCheckTriggered =
            (System.currentTimeMillis() - lastAutomatedUpdate) < updateInterval.inWholeMilliseconds
        if (!wasPreviousUpdateCheckTriggered) {
            GlobalScope.launch(Dispatchers.IO) {
                task()
            }
        }

        currentUpdateTaskId =
            HAScheduler.scheduleCron(task, "0 */${updateInterval.inWholeHours} * * *", "webUI-update-checker")
    }

    suspend fun setupWebUI() {
        if (serverConfig.webUIFlavor.value == WebUIFlavor.CUSTOM) {
            return
        }

        val flavor = WebUIFlavor.current
        val servedFlavor = getServedWebUIFlavor()

        val log =
            KotlinLogging.logger("${logger.name} setupWebUI(flavor= ${flavor.uiName}, servedFlavor= ${servedFlavor.uiName})")

        if (doesLocalWebUIExist(applicationDirs.webUIRoot)) {
            val currentVersion = getLocalVersion()

            log.info { "found webUI files - version= $currentVersion" }

            val hasFlavorChanged = flavor.uiName != servedFlavor.uiName
            if (hasFlavorChanged) {
                try {
                    doInitialSetup(flavor)
                    return
                } catch (e: Exception) {
                    log.warn(e) { "Failed to install the version of the new flavor, proceeding with version of previous flavor" }
                }
            }

            val flavorToValidate = if (hasFlavorChanged) servedFlavor else flavor
            if (!isLocalWebUIValid(flavorToValidate, applicationDirs.webUIRoot)) {
                try {
                    doInitialSetup(flavorToValidate, isInvalid = true)
                } catch (e: Exception) {
                    log.warn(e) { "WebUI is invalid and failed to install a valid version, proceeding with invalid version" }
                }
                return
            }

            if (isAutoUpdateEnabled()) {
                checkForUpdate(flavor)
            }

            // check if the bundled webUI version is a newer version than the current used version
            // this could be the case in case no compatible webUI version is available and a newer server version was installed
            val shouldUpdateToBundledVersion =
                flavor.isDefault() && extractVersion(getLocalVersion()) < extractVersion(BuildConfig.WEBUI_TAG)
            if (shouldUpdateToBundledVersion) {
                log.debug { "update to bundled version \"${BuildConfig.WEBUI_TAG}\"" }

                try {
                    setupBundledWebUI()
                } catch (e: Exception) {
                    log.error(e) { "failed the update to the bundled webUI" }
                }
            }

            return
        }

        log.warn { "no webUI files found, starting download..." }
        try {
            doInitialSetup(flavor)
        } catch (e: Exception) {
            log.error(e) {
                "Failed to setup the webUI. Unable to start the server with a served webUI, change the settings to start" +
                    "without one. Stopping the server now..."
            }
            shutdownApp(WebUISetupFailure)
        }
    }

    /**
     * Tries to download the latest compatible version for the selected webUI and falls back to the default webUI in case of errors.
     */
    private suspend fun doInitialSetup(
        flavor: WebUIFlavor,
        isInvalid: Boolean = false,
    ) {
        val log =
            KotlinLogging.logger("${logger.name} doInitialSetup(flavor= ${flavor.uiName})")

        val isLocalWebUIValid = !isInvalid && isLocalWebUIValid(flavor, applicationDirs.webUIRoot)

        /**
         * Performs the download and returns if the download was successful.
         *
         * In case the download failed but the local webUI is valid the download is considered a success to prevent the fallback logic
         */
        val doDownload: suspend (getVersion: suspend () -> String) -> Boolean = { getVersion ->
            try {
                downloadVersion(flavor, getVersion())
                true
            } catch (e: Exception) {
                false
            } ||
                isLocalWebUIValid
        }

        // download the latest compatible version for the current selected webUI
        val fallbackToDefaultWebUI = !doDownload { getLatestCompatibleVersion(flavor) }
        if (!fallbackToDefaultWebUI) {
            return
        }

        if (!flavor.isDefault()) {
            log.warn { "fallback to default webUI \"${WebUIFlavor.default.uiName}\"" }

            serverConfig.webUIFlavor.value = WebUIFlavor.default

            val fallbackToBundledVersion = !doDownload { getLatestCompatibleVersion(flavor) }
            if (!fallbackToBundledVersion) {
                return
            }
        }

        log.warn { "fallback to bundled default webUI \"${WebUIFlavor.default.uiName}\"" }

        try {
            setupBundledWebUI()
        } catch (e: Exception) {
            throw Exception("Unable to setup a webUI")
        }
    }

    private suspend fun setupBundledWebUI() {
        try {
            extractBundledWebUI()
            setServedWebUIFlavor(WebUIFlavor.default)
            return
        } catch (e: BundledWebUIMissing) {
            logger.warn(e) { "setupBundledWebUI: fallback to downloading the version of the bundled webUI" }
        }

        downloadVersion(WebUIFlavor.default, BuildConfig.WEBUI_TAG)
    }

    private fun extractBundledWebUI() {
        val resourceWebUI: InputStream =
            BuildConfig::class.java.getResourceAsStream("/WebUI.zip") ?: throw BundledWebUIMissing()

        logger.info { "extractBundledWebUI: Using the bundled WebUI zip..." }

        val webUIZip = WebUIFlavor.default.baseFileName
        val webUIZipPath = "$tmpDir/$webUIZip"
        val webUIZipFile = File(webUIZipPath)
        resourceWebUI.use { input ->
            webUIZipFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        File(applicationDirs.webUIRoot).deleteRecursively()
        extractDownload(webUIZipPath, applicationDirs.webUIRoot)
    }

    private suspend fun checkForUpdate(flavor: WebUIFlavor) {
        preferences.edit().putLong(LAST_WEBUI_UPDATE_CHECK_KEY, System.currentTimeMillis()).apply()
        val localVersion = getLocalVersion()

        val log =
            KotlinLogging.logger("${logger.name} checkForUpdate(flavor= ${flavor.uiName}, localVersion= $localVersion)")

        if (!isUpdateAvailable(flavor, localVersion).second) {
            log.debug { "local version is the latest one" }
            return
        }

        log.info { "An update is available, starting download..." }
        try {
            downloadVersion(flavor, getLatestCompatibleVersion(flavor))
        } catch (e: Exception) {
            log.warn(e) { "failed due to" }
        }
    }

    private fun getDownloadUrlFor(
        flavor: WebUIFlavor,
        version: String,
    ): String {
        val baseReleasesUrl = "${flavor.repoUrl}/releases"
        val downloadSpecificVersionBaseUrl = "$baseReleasesUrl/download"

        return "$downloadSpecificVersionBaseUrl/$version"
    }

    private fun getLocalVersion(path: String = applicationDirs.webUIRoot): String =
        try {
            File("$path/revision").readText().trim()
        } catch (e: Exception) {
            "r-1"
        }

    private fun doesLocalWebUIExist(path: String): Boolean {
        // check if we have webUI installed and is correct version
        val webUIRevisionFile = File("$path/revision")
        return webUIRevisionFile.exists()
    }

    private suspend fun isLocalWebUIValid(
        flavor: WebUIFlavor,
        path: String,
    ): Boolean {
        if (!doesLocalWebUIExist(path)) {
            return false
        }

        val log =
            KotlinLogging.logger("${logger.name} isLocalWebUIValid(flavor= ${flavor.uiName}, path= $path)")
        log.info { "Verifying WebUI files..." }

        val currentVersion = getLocalVersion(path)
        val localMD5Sum = getLocalMD5Sum(path)
        val currentVersionMD5Sum = fetchMD5SumFor(flavor, currentVersion)
        val validationSucceeded = currentVersionMD5Sum == localMD5Sum

        log.info {
            "Validation " +
                "${if (validationSucceeded) "succeeded" else "failed"} - " +
                "md5: local= $localMD5Sum; expected= $currentVersionMD5Sum"
        }

        return validationSucceeded
    }

    private fun getLocalMD5Sum(fileDir: String): String {
        var sum = ""
        File(fileDir).walk().toList().sortedBy { it.path }.forEach { file ->
            if (file.isFile) {
                val md5 = MessageDigest.getInstance("MD5")
                md5.update(file.readBytes())
                val digest = md5.digest()
                sum += digest.toHex()
            }
        }

        val md5 = MessageDigest.getInstance("MD5")
        md5.update(sum.toByteArray(StandardCharsets.UTF_8))
        val digest = md5.digest()
        return digest.toHex()
    }

    private suspend fun <T> executeWithRetry(
        log: KLogger,
        execute: suspend () -> T,
        maxRetries: Int = 3,
        retryCount: Int = 0,
    ): T {
        try {
            return execute()
        } catch (e: Exception) {
            log.warn(e) { "(retry $retryCount/$maxRetries) failed due to" }

            if (retryCount < maxRetries) {
                return executeWithRetry(log, execute, maxRetries, retryCount + 1)
            }

            throw e
        }
    }

    private suspend fun fetchMD5SumFor(
        flavor: WebUIFlavor,
        version: String,
    ): String =
        try {
            executeWithRetry(KotlinLogging.logger("${logger.name} fetchMD5SumFor(flavor= ${flavor.uiName}, version= $version)"), {
                network.client
                    .newCall(GET("${getDownloadUrlFor(flavor, version)}/md5sum"))
                    .awaitSuccess()
                    .body
                    .string()
                    .trim()
            })
        } catch (e: Exception) {
            ""
        }

    private fun extractVersion(versionString: String): Int {
        // version string is of format "r<number>"
        return versionString.substring(1).toInt()
    }

    private suspend fun fetchPreviewVersion(flavor: WebUIFlavor): String =
        executeWithRetry(KotlinLogging.logger("${logger.name} fetchPreviewVersion(${flavor.uiName})"), {
            val releaseInfoJson =
                network.client
                    .newCall(GET(flavor.latestReleaseInfoUrl))
                    .awaitSuccess()
                    .body
                    .string()
            Json.decodeFromString<JsonObject>(releaseInfoJson)["tag_name"]?.jsonPrimitive?.content
                ?: throw Exception("Failed to get the preview version tag")
        })

    private suspend fun fetchServerMappingFile(flavor: WebUIFlavor): JsonArray =
        executeWithRetry(
            KotlinLogging.logger("$logger fetchServerMappingFile(${flavor.uiName})"),
            {
                json
                    .parseToJsonElement(
                        network.client
                            .newCall(GET(flavor.versionMappingUrl))
                            .awaitSuccess()
                            .body
                            .string(),
                    ).jsonArray
            },
        )

    private suspend fun getLatestCompatibleVersion(flavor: WebUIFlavor): String {
        if (serverConfig.webUIChannel.value == WebUIChannel.BUNDLED) {
            logger.debug { "getLatestCompatibleVersion: Channel is \"${WebUIChannel.BUNDLED}\", do not check for update" }
            return BuildConfig.WEBUI_TAG
        }

        val currentServerVersionNumber =
            BuildConfig.VERSION
                .split(".")
                .last()
                .toInt()
        val webUIToServerVersionMappings = fetchServerMappingFile(flavor)

        logger.debug {
            "getLatestCompatibleVersion: " +
                "flavor= ${flavor.uiName}, " +
                "webUIChannel= ${serverConfig.webUIChannel.value}, " +
                "currentServerVersion= ${BuildConfig.VERSION}, " +
                "mappingFile= $webUIToServerVersionMappings"
        }

        for (i in 0 until webUIToServerVersionMappings.size) {
            val webUIToServerVersionEntry = webUIToServerVersionMappings[i].jsonObject
            var webUIVersion =
                webUIToServerVersionEntry["uiVersion"]?.jsonPrimitive?.content
                    ?: throw Exception("Invalid mappingFile")
            val minServerVersionString =
                webUIToServerVersionEntry["serverVersion"]
                    ?.jsonPrimitive
                    ?.content
                    ?: throw Exception("Invalid mappingFile")
            val minServerVersionNumber = extractVersion(minServerVersionString)

            // is a STABLE webUI release, without a specified webUI version, which requires same handling as the PREVIEW release
            val isUnknownStableVersion = webUIVersion == "STABLEPREVIEW"

            if (serverConfig.webUIChannel.value != WebUIChannel.from(webUIVersion)) {
                // allow only STABLE versions for STABLE channel
                if (serverConfig.webUIChannel.value == WebUIChannel.STABLE && !isUnknownStableVersion) {
                    continue
                }

                // allow all versions for PREVIEW channel
            }

            if (webUIVersion == WebUIChannel.PREVIEW.name || isUnknownStableVersion) {
                webUIVersion = fetchPreviewVersion(flavor)
            }

            val isNewerThanBundled =
                !flavor.isDefault() || extractVersion(webUIVersion) >= extractVersion(BuildConfig.WEBUI_TAG)
            val isCompatibleVersion = minServerVersionNumber <= currentServerVersionNumber && isNewerThanBundled
            if (isCompatibleVersion) {
                return webUIVersion
            }
        }

        throw Exception("No compatible webUI version found")
    }

    private fun emitStatus(
        version: String,
        state: UpdateState,
        progress: Int,
        immediate: Boolean = false,
    ) {
        scope.launch {
            val status = getStatus(version, state, progress)

            if (immediate) {
                statusFlow.emit(status)
                return@launch
            }

            notifyFlow.emit(status)
        }
    }

    fun startDownloadInScope(
        flavor: WebUIFlavor,
        version: String,
    ) {
        scope.launch {
            downloadVersion(flavor, version)
        }
    }

    suspend fun downloadVersion(
        flavor: WebUIFlavor,
        version: String,
    ) {
        emitStatus(version, DOWNLOADING, 0, immediate = true)

        try {
            val webUIZip = "${flavor.baseFileName}-$version.zip"
            val webUIZipPath = "$tmpDir/$webUIZip"
            val webUIZipURL = "${getDownloadUrlFor(flavor, version)}/$webUIZip"

            val log =
                KotlinLogging.logger("${logger.name} downloadVersion(version= $version, flavor= ${flavor.uiName})")
            log.info { "Downloading WebUI zip from the Internet..." }

            executeWithRetry(log, {
                downloadVersionZipFile(flavor, webUIZipURL, webUIZipPath) { progress ->
                    emitStatus(
                        version,
                        DOWNLOADING,
                        progress,
                    )
                }
            })
            File(applicationDirs.webUIRoot).deleteRecursively()

            // extract webUI zip
            log.info { "Extracting WebUI zip..." }
            extractDownload(webUIZipPath, applicationDirs.webUIRoot)
            log.info { "Extracting WebUI zip Done." }

            setServedWebUIFlavor(flavor)

            emitStatus(version, FINISHED, 100, immediate = true)

            serveWebUI()
        } catch (e: Exception) {
            emitStatus(version, ERROR, 0, immediate = true)
            throw e
        }
    }

    private suspend fun downloadVersionZipFile(
        flavor: WebUIFlavor,
        url: String,
        filePath: String,
        updateProgress: (progress: Int) -> Unit,
    ) {
        val zipFile = File(filePath)
        zipFile.delete()

        val data = ByteArray(1024)

        zipFile.outputStream().use { webUIZipFileOut ->

            val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            connection.connect()
            val contentLength = connection.contentLength

            connection.inputStream.buffered().use { inp ->
                var totalCount = 0

                print("downloadVersionZipFile(${flavor.uiName}): Download progress: % 00")
                while (true) {
                    val count = inp.read(data, 0, 1024)

                    if (count == -1) {
                        break
                    }

                    totalCount += count
                    val percentage = (totalCount.toFloat() / contentLength * 100).toInt()
                    val percentageStr = percentage.toString().padStart(2, '0')
                    print("\b\b$percentageStr")

                    webUIZipFileOut.write(data, 0, count)

                    updateProgress(percentage)
                }
                println()
                logger.info { "downloadVersionZipFile(${flavor.uiName}): Downloading WebUI Done." }
            }
        }

        if (!isDownloadValid(flavor, filePath)) {
            throw Exception("Download is invalid")
        }
    }

    private suspend fun isDownloadValid(
        flavor: WebUIFlavor,
        zipFilePath: String,
    ): Boolean {
        val tempUnzippedWebUIFolderPath = zipFilePath.replace(".zip", "")

        extractDownload(zipFilePath, tempUnzippedWebUIFolderPath)

        val isDownloadValid = isLocalWebUIValid(flavor, tempUnzippedWebUIFolderPath)

        File(tempUnzippedWebUIFolderPath).deleteRecursively()

        return isDownloadValid
    }

    private fun extractDownload(
        zipFilePath: String,
        targetPath: String,
    ) {
        File(targetPath).mkdirs()
        ZipFile(zipFilePath).use { it.extractAll(targetPath) }
    }

    suspend fun isUpdateAvailable(
        flavor: WebUIFlavor,
        currentVersion: String = getLocalVersion(),
        raiseError: Boolean = false,
    ): Pair<String, Boolean> =
        try {
            val isServedWebUIForCurrentFlavor = flavor.uiName == getServedWebUIFlavor().uiName
            val latestCompatibleVersion = getLatestCompatibleVersion(flavor)
            val isVersionUpdateAvailable = latestCompatibleVersion != currentVersion

            val isUpdateAvailable = !isServedWebUIForCurrentFlavor || isVersionUpdateAvailable
            Pair(latestCompatibleVersion, isUpdateAvailable)
        } catch (e: Exception) {
            logger.warn(e) { "isUpdateAvailable: check failed due to" }

            if (raiseError) {
                throw e
            }

            Pair("", false)
        }
}

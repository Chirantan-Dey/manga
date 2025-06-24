/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { fromEvent } from 'file-selector';
import IconButton from '@mui/material/IconButton';
import AddIcon from '@mui/icons-material/Add';
import { StringParam, useQueryParam } from 'use-query-params';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useWindowEvent } from '@mantine/hooks';
import { CustomTooltip } from '@/modules/core/components/CustomTooltip.tsx';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import { AppbarSearch } from '@/modules/core/components/AppbarSearch.tsx';
import { LoadingPlaceholder } from '@/modules/core/components/feedback/LoadingPlaceholder.tsx';
import { makeToast } from '@/modules/core/utils/Toast.ts';
import { LanguageSelect } from '@/modules/core/components/inputs/LanguageSelect.tsx';
import { ExtensionCard } from '@/modules/extension/components/ExtensionCard.tsx';
import { StyledGroupedVirtuoso } from '@/modules/core/components/virtuoso/StyledGroupedVirtuoso.tsx';
import { StyledGroupHeader } from '@/modules/core/components/virtuoso/StyledGroupHeader.tsx';
import { StyledGroupItemWrapper } from '@/modules/core/components/virtuoso/StyledGroupItemWrapper.tsx';
import { EmptyViewAbsoluteCentered } from '@/modules/core/components/feedback/EmptyViewAbsoluteCentered.tsx';
import { defaultPromiseErrorHandler } from '@/lib/DefaultPromiseErrorHandler.ts';
import { VirtuosoUtil } from '@/lib/virtuoso/Virtuoso.util.tsx';
import {
    groupExtensionsByLanguage,
    getLanguagesFromExtensions,
    translateExtensionLanguage,
    filterExtensions,
} from '@/modules/extension/Extensions.utils.ts';
import {
    ExtensionAction,
    ExtensionGroupState,
    ExtensionState,
    TExtension,
} from '@/modules/extension/Extensions.types.ts';
import { EXTENSION_ACTION_TO_FAILURE_TRANSLATION_KEY_MAP } from '@/modules/extension/Extensions.constants.ts';
import { AppRoutes } from '@/modules/core/AppRoute.constants.ts';
import { getErrorMessage } from '@/lib/HelperFunctions.ts';
import {
    createUpdateMetadataServerSettings,
    useMetadataServerSettings,
} from '@/modules/settings/services/ServerSettingsMetadata.ts';
import { MetadataBrowseSettings } from '@/modules/browse/Browse.types.ts';
import { useAppAction } from '@/modules/navigation-bar/hooks/useAppAction.ts';

const LANGUAGE = 0;
const EXTENSIONS = 1;

const GroupHeader = ({
    groupName,
    isFirstItem,
    groupExtensionIds,
    isUpdateGroup,
    updatingExtensionIds,
    setUpdatingExtensionIds,
    handleExtensionUpdate,
}: {
    groupName: string;
    isFirstItem: boolean;
    groupExtensionIds: TExtension['pkgName'][];
    isUpdateGroup: boolean;
    updatingExtensionIds: TExtension['pkgName'][];
    setUpdatingExtensionIds: (ids: TExtension['pkgName'][]) => void;
    handleExtensionUpdate: () => void;
}) => {
    const { t } = useTranslation();

    return (
        <StyledGroupHeader
            key={groupName}
            isFirstItem={isFirstItem}
            sx={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', pr: 1 }}
        >
            <Typography variant="h5" component="h2">
                {translateExtensionLanguage(groupName)}
            </Typography>
            {isUpdateGroup && (
                <Button
                    disabled={!!updatingExtensionIds.length}
                    variant="contained"
                    onClick={() => {
                        setUpdatingExtensionIds(groupExtensionIds);

                        requestManager
                            .updateExtensions(groupExtensionIds, { update: true })
                            .response.then(() => handleExtensionUpdate())
                            .catch((e) =>
                                makeToast(
                                    t(EXTENSION_ACTION_TO_FAILURE_TRANSLATION_KEY_MAP[ExtensionAction.UPDATE], {
                                        count: groupExtensionIds.length,
                                    }),
                                    'error',
                                    getErrorMessage(e),
                                ),
                            )
                            .finally(() => setUpdatingExtensionIds([]));
                    }}
                >
                    {t('extension.action.label.update_all')}
                </Button>
            )}
        </StyledGroupHeader>
    );
};

export function Extensions({ tabsMenuHeight }: { tabsMenuHeight: number }) {
    const { t } = useTranslation();

    const {
        data: serverSettingsData,
        loading: areServerSettingsLoading,
        error: serverSettingsError,
        refetch: refetchServerSettings,
    } = requestManager.useGetServerSettings({ notifyOnNetworkStatusChange: true });
    const [fetchExtensions, { data, loading: areExtensionsLoading, error: extensionsError }] =
        requestManager.useExtensionListFetch();

    const {
        settings: { extensionLanguages: shownLangs, showNsfw },
    } = useMetadataServerSettings();
    const updateMetadataServerSettings = createUpdateMetadataServerSettings<
        keyof Pick<MetadataBrowseSettings, 'extensionLanguages'>
    >((e) => makeToast(t('global.error.label.failed_to_save_changes'), 'error', getErrorMessage(e)));

    const [query] = useQueryParam('query', StringParam);

    const [updatingExtensionIds, setUpdatingExtensionIds] = useState<string[]>([]);
    const [refetchExtensions, setRefetchExtensions] = useState({});

    const isLoading = areServerSettingsLoading || areExtensionsLoading;
    const error = serverSettingsError ?? extensionsError;

    const areReposDefined = !!serverSettingsData?.settings.extensionRepos.length;
    const areMultipleReposInUse = (serverSettingsData?.settings.extensionRepos.length ?? 0) > 1;

    const allExtensions = data?.fetchExtensions?.extensions;
    const allLangs = useMemo(() => getLanguagesFromExtensions(allExtensions ?? []), [allExtensions]);

    const filteredExtensions = useMemo(
        () => filterExtensions(allExtensions ?? [], { selectedLanguages: shownLangs, showNsfw, query }),
        [allExtensions, shownLangs, showNsfw, query],
    );
    const groupedExtensions = useMemo(() => groupExtensionsByLanguage(filteredExtensions), [filteredExtensions]);
    const groupCounts = useMemo(
        () => groupedExtensions.map((extensionGroup) => extensionGroup[EXTENSIONS].length),
        [groupedExtensions],
    );
    const visibleExtensions = useMemo(
        () => groupedExtensions.map(([, extensions]) => extensions).flat(1),
        [groupedExtensions],
    );

    const computeItemKey = VirtuosoUtil.useCreateGroupedComputeItemKey(
        groupCounts,
        useCallback((index) => groupedExtensions[index][LANGUAGE], [groupedExtensions]),
        useCallback((index) => visibleExtensions[index].pkgName, [visibleExtensions]),
    );

    const handleExtensionUpdate = useCallback(() => setRefetchExtensions({}), []);

    const submitExternalExtension = (file: File) => {
        if (!file.name.toLowerCase().endsWith('apk')) {
            makeToast(t('global.error.label.invalid_file_type'), 'error');
            return;
        }

        makeToast(t('extension.label.installing_file'), 'info');
        requestManager
            .installExternalExtension(file)
            .response.then(() => {
                handleExtensionUpdate();
                makeToast(t('extension.label.installed_successfully'), 'success');
            })
            .catch((e) => makeToast(t('extension.label.installation_failed'), 'error', getErrorMessage(e)));
    };

    useEffect(() => {
        fetchExtensions();
    }, [refetchExtensions]);

    useAppAction(
        <>
            <AppbarSearch />
            <CustomTooltip title={t('extension.action.label.install_external')}>
                <IconButton
                    onClick={() => {
                        const input = document.createElement('input');
                        input.style.display = 'none';
                        input.type = 'file';
                        input.onchange = () => {
                            const file = input.files?.[0];
                            if (file) {
                                submitExternalExtension(file);
                            }
                        };

                        document.documentElement.appendChild(input);
                        input.click();
                        document.documentElement.removeChild(input);
                    }}
                    color="inherit"
                >
                    <AddIcon />
                </IconButton>
            </CustomTooltip>

            <LanguageSelect
                selectedLanguages={shownLangs}
                setSelectedLanguages={(languages: string[]) =>
                    updateMetadataServerSettings('extensionLanguages', languages)
                }
                languages={allLangs}
            />
        </>,
        [t, shownLangs, allLangs],
    );

    useWindowEvent('drop', async (e) => {
        e.preventDefault();
        const files = await fromEvent(e);
        submitExternalExtension(files[0] as File);
    });
    useWindowEvent('dragover', (e) => {
        e.preventDefault();
    });

    if (isLoading) {
        return <LoadingPlaceholder />;
    }

    if (error) {
        return (
            <EmptyViewAbsoluteCentered
                message={t('global.error.label.failed_to_load_data')}
                messageExtra={getErrorMessage(error)}
                retry={() => {
                    if (serverSettingsError) {
                        refetchServerSettings().catch(defaultPromiseErrorHandler('Extensions::refetchServerSettings'));
                    }

                    if (extensionsError) {
                        fetchExtensions().catch(defaultPromiseErrorHandler('Extensions::refetchExtensions'));
                    }
                }}
            />
        );
    }

    const showAddRepoInfo = !allExtensions?.length && !areReposDefined;
    if (showAddRepoInfo) {
        return (
            <Stack
                sx={{
                    alignItems: 'center',
                    justifyContent: 'center',
                    rowGap: '10px',
                    paddingTop: '20px',
                }}
            >
                <Typography>{t('extension.label.add_repository_info')}</Typography>
                <Button component={Link} variant="contained" to={AppRoutes.settings.childRoutes.browse.path}>
                    {t('settings.title')}
                </Button>
            </Stack>
        );
    }

    return (
        <StyledGroupedVirtuoso
            persistKey="extensions"
            heightToSubtract={tabsMenuHeight}
            overscan={window.innerHeight * 0.5}
            groupCounts={groupCounts}
            groupContent={(index) => {
                const [groupName, groupExtensions] = groupedExtensions[index];
                const isUpdateGroup = groupName === ExtensionGroupState.UPDATE_PENDING;

                return (
                    <GroupHeader
                        groupName={groupName}
                        isFirstItem={index === 0}
                        groupExtensionIds={groupExtensions.map((extension) => extension.pkgName)}
                        isUpdateGroup={isUpdateGroup}
                        updatingExtensionIds={updatingExtensionIds}
                        setUpdatingExtensionIds={setUpdatingExtensionIds}
                        handleExtensionUpdate={handleExtensionUpdate}
                    />
                );
            }}
            computeItemKey={computeItemKey}
            itemContent={(index) => {
                const item = visibleExtensions[index];

                return (
                    <StyledGroupItemWrapper>
                        <ExtensionCard
                            extension={item}
                            handleUpdate={handleExtensionUpdate}
                            showSourceRepo={areMultipleReposInUse}
                            forcedState={
                                updatingExtensionIds.includes(item.pkgName) ? ExtensionState.UPDATING : undefined
                            }
                        />
                    </StyledGroupItemWrapper>
                );
            }}
        />
    );
}

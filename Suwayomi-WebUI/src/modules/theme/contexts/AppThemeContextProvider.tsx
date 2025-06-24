/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { ReactNode, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Direction, ThemeProvider, useColorScheme } from '@mui/material/styles';
import { CacheProvider } from '@emotion/react';
import { useTranslation } from 'react-i18next';
import { AppThemeContext, TAppThemeContext, ThemeMode } from '@/modules/theme/contexts/AppThemeContext.tsx';
import { DIRECTION_TO_CACHE } from '@/modules/theme/ThemeDirectionCache.ts';
import {
    createUpdateMetadataServerSettings,
    useMetadataServerSettings,
} from '@/modules/settings/services/ServerSettingsMetadata.ts';
import { MediaQuery } from '@/modules/core/utils/MediaQuery.tsx';
import { getTheme } from '@/modules/theme/services/AppThemes.ts';
import { createAndSetTheme } from '@/modules/theme/services/ThemeCreator.ts';
import { makeToast } from '@/modules/core/utils/Toast.ts';
import { getErrorMessage } from '@/lib/HelperFunctions.ts';

export const AppThemeContextProvider = ({ children }: { children: ReactNode }) => {
    const { t, i18n } = useTranslation();
    const { mode } = useColorScheme();
    const {
        settings: { appTheme, themeMode, shouldUsePureBlackMode, customThemes },
    } = useMetadataServerSettings();

    const directionRef = useRef<Direction>('ltr');

    const [systemThemeMode, setSystemThemeMode] = useState<ThemeMode>(MediaQuery.getSystemThemeMode());
    const [dynamicColor, setDynamicColor] = useState<TAppThemeContext['dynamicColor']>(null);

    const actualThemeMode = mode ?? themeMode ?? 'dark';
    const currentDirection = i18n.dir();

    const updateSetting = createUpdateMetadataServerSettings<'appTheme' | 'themeMode' | 'shouldUsePureBlackMode'>((e) =>
        makeToast(t('global.error.label.failed_to_save_changes'), 'error', getErrorMessage(e)),
    );

    const appThemeContext = useMemo(
        () =>
            ({
                appTheme,
                setAppTheme: (value) => updateSetting('appTheme', value),
                themeMode,
                setThemeMode: (value) => updateSetting('themeMode', value),
                shouldUsePureBlackMode,
                setShouldUsePureBlackMode: (value) => updateSetting('shouldUsePureBlackMode', value),
                dynamicColor,
                setDynamicColor,
            }) satisfies TAppThemeContext,
        [themeMode, shouldUsePureBlackMode, appTheme, dynamicColor],
    );

    const theme = useMemo(
        () =>
            createAndSetTheme(
                actualThemeMode as ThemeMode,
                getTheme(appTheme, customThemes),
                shouldUsePureBlackMode,
                currentDirection,
                dynamicColor,
            ),
        [
            actualThemeMode,
            currentDirection,
            systemThemeMode,
            shouldUsePureBlackMode,
            appTheme,
            customThemes,
            dynamicColor,
        ],
    );

    useLayoutEffect(() => {
        const unsubscribe = MediaQuery.listenToSystemThemeChange(setSystemThemeMode);

        return () => unsubscribe();
    }, []);

    if (directionRef.current !== currentDirection) {
        document.dir = currentDirection;
        directionRef.current = currentDirection;
    }

    return (
        <AppThemeContext.Provider value={appThemeContext}>
            <CacheProvider value={DIRECTION_TO_CACHE[currentDirection]}>
                <ThemeProvider theme={theme}>{children}</ThemeProvider>
            </CacheProvider>
        </AppThemeContext.Provider>
    );
};

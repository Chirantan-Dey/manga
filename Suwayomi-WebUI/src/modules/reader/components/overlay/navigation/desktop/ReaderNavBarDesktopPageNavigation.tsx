/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import Stack from '@mui/material/Stack';
import { useTranslation } from 'react-i18next';
import MenuItem from '@mui/material/MenuItem';
import { memo, useMemo } from 'react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import { Select } from '@/modules/core/components/inputs/Select.tsx';
import { getNextIndexFromPage, getPage } from '@/modules/reader/utils/ReaderProgressBar.utils.tsx';
import { ReaderStatePages } from '@/modules/reader/types/ReaderProgressBar.types.ts';
import { ReaderControls } from '@/modules/reader/services/ReaderControls.ts';
import { useGetOptionForDirection } from '@/modules/theme/services/ThemeCreator.ts';
import { ReaderService } from '@/modules/reader/services/ReaderService.ts';
import { ReaderNavBarDesktopNextPreviousButton } from '@/modules/reader/components/overlay/navigation/desktop/ReaderNavBarDesktopNextPreviousButton.tsx';
import { READING_DIRECTION_TO_THEME_DIRECTION } from '@/modules/reader/constants/ReaderSettings.constants.tsx';
import { IReaderSettings } from '@/modules/reader/types/Reader.types.ts';
import { withPropsFrom } from '@/modules/core/hoc/withPropsFrom.tsx';
import { userReaderStatePagesContext } from '@/modules/reader/contexts/state/ReaderStatePagesContext.tsx';

const BaseReaderNavBarDesktopPageNavigation = ({
    currentPageIndex,
    pages,
    readingDirection,
    openPage,
}: Pick<ReaderStatePages, 'currentPageIndex' | 'pages'> &
    Pick<IReaderSettings, 'readingDirection'> & {
        openPage: ReturnType<typeof ReaderControls.useOpenPage>;
    }) => {
    const { t } = useTranslation();
    const getOptionForDirection = useGetOptionForDirection();
    const currentPage = useMemo(() => getPage(currentPageIndex, pages), [currentPageIndex, pages]);

    const direction = READING_DIRECTION_TO_THEME_DIRECTION[readingDirection];

    return (
        <Stack sx={{ flexDirection: 'row', gap: 1 }} dir="ltr">
            <ReaderNavBarDesktopNextPreviousButton
                type="previous"
                title={t(getOptionForDirection('reader.button.previous_page', 'reader.button.next_page', direction))}
                disabled={getOptionForDirection(
                    !currentPage.primary.index,
                    getNextIndexFromPage(currentPage) === getNextIndexFromPage(pages.slice(-1)[0]),
                    direction,
                )}
                onClick={() => openPage('previous', undefined, false)}
            />
            <FormControl sx={{ flexBasis: '70%', flexGrow: 0, flexShrink: 0 }}>
                <InputLabel id="reader-nav-bar-desktop-page-select">{t('reader.page_info.label.page')}</InputLabel>
                <Select
                    labelId="reader-nav-bar-desktop-page-select"
                    label={t('reader.page_info.label.page')}
                    value={getNextIndexFromPage(currentPage)}
                    onChange={(e) => openPage(e.target.value as number, undefined, false)}
                >
                    {pages.map((page) => (
                        <MenuItem key={getNextIndexFromPage(page)} value={getNextIndexFromPage(page)}>
                            {page.name}
                        </MenuItem>
                    ))}
                </Select>
            </FormControl>
            <ReaderNavBarDesktopNextPreviousButton
                type="next"
                title={t(getOptionForDirection('reader.button.next_page', 'reader.button.previous_page', direction))}
                disabled={getOptionForDirection(
                    getNextIndexFromPage(currentPage) === getNextIndexFromPage(pages.slice(-1)[0]),
                    !currentPage.primary.index,
                    direction,
                )}
                onClick={() => openPage('next', undefined, false)}
            />
        </Stack>
    );
};

export const ReaderNavBarDesktopPageNavigation = withPropsFrom(
    memo(BaseReaderNavBarDesktopPageNavigation),
    [
        userReaderStatePagesContext,
        () => ({ openPage: ReaderControls.useOpenPage() }),
        ReaderService.useSettingsWithoutDefaultFlag,
    ],
    ['currentPageIndex', 'pages', 'readingDirection', 'openPage'],
);

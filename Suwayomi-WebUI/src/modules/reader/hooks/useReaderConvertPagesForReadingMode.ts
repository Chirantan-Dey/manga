/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useLayoutEffect, useState } from 'react';
import { ReaderPageSpreadState, ReadingMode } from '@/modules/reader/types/Reader.types.ts';
import { getNextIndexFromPage, getPage } from '@/modules/reader/utils/ReaderProgressBar.utils.tsx';
import { createPagesData } from '@/modules/reader/utils/ReaderPager.utils.tsx';
import { ReaderStatePages } from '@/modules/reader/types/ReaderProgressBar.types.ts';
import { ReaderControls } from '@/modules/reader/services/ReaderControls.ts';

export const useReaderConvertPagesForReadingMode = (
    currentPageIndex: number,
    actualPages: ReaderStatePages['pages'],
    pageUrls: ReaderStatePages['pageUrls'],
    setPages: ReaderStatePages['setPages'],
    setPagesToSpreadState: (states: ReaderPageSpreadState[]) => void,
    updateCurrentPageIndex: ReturnType<typeof ReaderControls.useUpdateCurrentPageIndex>,
    readingMode: ReadingMode,
) => {
    const [wasDoublePageMode, setWasDoublePageMode] = useState(readingMode === ReadingMode.DOUBLE_PAGE);

    useLayoutEffect(() => {
        const convertPagesToNormalPageMode = wasDoublePageMode && readingMode !== ReadingMode.DOUBLE_PAGE;
        if (convertPagesToNormalPageMode) {
            setWasDoublePageMode(false);

            const newPageData = createPagesData(pageUrls);
            setPages(newPageData);
            setPagesToSpreadState(newPageData.map(({ primary: { url } }) => ({ url, isSpread: false })));
            return;
        }

        const convertPagesToDoublePageMode = readingMode === ReadingMode.DOUBLE_PAGE;
        if (convertPagesToDoublePageMode) {
            if (!wasDoublePageMode) {
                updateCurrentPageIndex(getNextIndexFromPage(getPage(currentPageIndex, actualPages)));
            }
            setPages(actualPages);
            setWasDoublePageMode(readingMode === ReadingMode.DOUBLE_PAGE);
        }
    }, [actualPages, readingMode]);
};

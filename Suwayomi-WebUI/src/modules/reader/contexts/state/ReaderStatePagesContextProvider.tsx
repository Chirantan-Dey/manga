/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { ReactNode, useMemo, useState } from 'react';
import { ReaderStatePagesContext } from '@/modules/reader/contexts/state/ReaderStatePagesContext.tsx';
import { ReaderStatePages } from '@/modules/reader/types/ReaderProgressBar.types.ts';
import { READER_STATE_PAGES_DEFAULTS } from '@/modules/reader/constants/ReaderContext.constants.ts';

export const ReaderStatePagesContextProvider = ({ children }: { children: ReactNode }) => {
    const [totalPages, setTotalPages] = useState<ReaderStatePages['totalPages']>(
        READER_STATE_PAGES_DEFAULTS.totalPages,
    );
    const [currentPageIndex, setCurrentPageIndex] = useState<ReaderStatePages['currentPageIndex']>(
        READER_STATE_PAGES_DEFAULTS.currentPageIndex,
    );
    const [pageToScrollToIndex, setPageToScrollToIndex] = useState<ReaderStatePages['pageToScrollToIndex']>(
        READER_STATE_PAGES_DEFAULTS.pageToScrollToIndex,
    );
    const [pageUrls, setPageUrls] = useState<ReaderStatePages['pageUrls']>(READER_STATE_PAGES_DEFAULTS.pageUrls);
    const [pageLoadStates, setPageLoadStates] = useState<ReaderStatePages['pageLoadStates']>(
        READER_STATE_PAGES_DEFAULTS.pageLoadStates,
    );
    const [pages, setPages] = useState<ReaderStatePages['pages']>(READER_STATE_PAGES_DEFAULTS.pages);
    const [transitionPageMode, setTransitionPageMode] = useState<ReaderStatePages['transitionPageMode']>(
        READER_STATE_PAGES_DEFAULTS.transitionPageMode,
    );
    const [retryFailedPagesKeyPrefix, setRetryFailedPagesKeyPrefix] = useState<
        ReaderStatePages['retryFailedPagesKeyPrefix']
    >(READER_STATE_PAGES_DEFAULTS.retryFailedPagesKeyPrefix);

    const value = useMemo(
        () => ({
            totalPages,
            setTotalPages,
            currentPageIndex,
            setCurrentPageIndex,
            pageToScrollToIndex,
            setPageToScrollToIndex,
            pageUrls,
            setPageUrls,
            pageLoadStates,
            setPageLoadStates,
            pages,
            setPages,
            transitionPageMode,
            setTransitionPageMode,
            retryFailedPagesKeyPrefix,
            setRetryFailedPagesKeyPrefix,
        }),
        [
            totalPages,
            pages,
            currentPageIndex,
            pageToScrollToIndex,
            pageUrls,
            pageLoadStates,
            transitionPageMode,
            retryFailedPagesKeyPrefix,
        ],
    );

    return <ReaderStatePagesContext.Provider value={value}>{children}</ReaderStatePagesContext.Provider>;
};

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import MenuItem from '@mui/material/MenuItem';
import { useTranslation } from 'react-i18next';
import { useMetadataServerSettings } from '@/modules/settings/services/ServerSettingsMetadata.ts';
import { Mangas } from '@/modules/manga/services/Mangas.ts';
import { defaultPromiseErrorHandler } from '@/lib/DefaultPromiseErrorHandler.ts';
import { MangaType } from '@/lib/graphql/generated/graphql.ts';

const DownloadRange = {
    NEXT_1: 1,
    NEXT_5: 5,
    NEXT_10: 10,
    NEXT_25: 25,
    ALL: undefined,
};

export const ChaptersDownloadActionMenuItems = ({
    mangaIds,
    closeMenu,
}: {
    mangaIds: MangaType['id'][];
    closeMenu: () => void;
}) => {
    const { t } = useTranslation();

    const {
        settings: { downloadAheadLimit },
    } = useMetadataServerSettings();

    const handleSelect = (size?: number, onlyUnread: boolean = true, downloadAhead: boolean = false) => {
        Mangas.performAction('download', mangaIds, {
            downloadAhead,
            onlyUnread,
            size,
        }).catch(defaultPromiseErrorHandler('ChapterDownloadButton::handleSelect'));
        closeMenu?.();
    };

    return (
        <>
            <MenuItem onClick={() => handleSelect(DownloadRange.NEXT_1)}>
                {t('chapter.action.download.add.label.next')}
            </MenuItem>
            <MenuItem onClick={() => handleSelect(DownloadRange.NEXT_5)}>
                {t('chapter.action.download.add.label.next_five')}
            </MenuItem>
            <MenuItem onClick={() => handleSelect(DownloadRange.NEXT_10)}>
                {t('chapter.action.download.add.label.next_ten')}
            </MenuItem>
            <MenuItem onClick={() => handleSelect(DownloadRange.NEXT_25)}>
                {t('chapter.action.download.add.label.next_twentyfive')}
            </MenuItem>
            <MenuItem onClick={() => handleSelect(downloadAheadLimit, undefined, true)}>
                {t('chapter.action.download.add.label.ahead', { count: downloadAheadLimit })}
            </MenuItem>
            <MenuItem onClick={() => handleSelect(DownloadRange.ALL, true)}>
                {t('chapter.action.download.add.label.unread')}
            </MenuItem>
            <MenuItem onClick={() => handleSelect(DownloadRange.ALL, false)}>
                {t('chapter.action.download.add.label.all')}
            </MenuItem>
        </>
    );
};

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
import { useTheme } from '@mui/material/styles';
import { useCallback, useLayoutEffect, useState, Fragment } from 'react';
import Stack from '@mui/material/Stack';
import OpenInFullIcon from '@mui/icons-material/OpenInFull';
import Modal from '@mui/material/Modal';
import { bindPopover, bindTrigger, bindMenu, usePopupState } from 'material-ui-popup-state/hooks';
import { Vibrant } from 'node-vibrant/browser';
import { FastAverageColor } from 'fast-average-color';
import { Mangas } from '@/modules/manga/services/Mangas.ts';
import { SpinnerImage } from '@/modules/core/components/SpinnerImage.tsx';
import { MANGA_COVER_ASPECT_RATIO } from '@/modules/manga/Manga.constants.ts';
import { MangaThumbnailInfo } from '@/modules/manga/Manga.types.ts';
import { TAppThemeContext, useAppThemeContext } from '@/modules/theme/contexts/AppThemeContext.tsx';
import { useLongPress } from 'use-long-press';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import PhotoCameraIcon from '@mui/icons-material/PhotoCamera';
import RestoreIcon from '@mui/icons-material/Restore';
import IconButton from '@mui/material/IconButton';
import { Menu } from '@/modules/core/components/menu/Menu.tsx';
import { MenuItem } from '@/modules/core/components/menu/MenuItem.tsx';
import { useTranslation } from 'react-i18next';

export const Thumbnail = ({
  manga,
  mangaDynamicColorSchemes,
}: {
  manga: Partial<MangaThumbnailInfo>;
  mangaDynamicColorSchemes: boolean;
}) => {
  const theme = useTheme();
  const { setDynamicColor } = useAppThemeContext();
  const { t } = useTranslation();

  const fullscreenPopupState = usePopupState({ variant: 'popover', popupId: 'manga-thumbnail-fullscreen' });
  const actionMenuPopupState = usePopupState({ variant: 'popover', popupId: 'manga-thumbnail-action-menu' });

  const [isImageReady, setIsImageReady] = useState(false);

  useLayoutEffect(() => {
    if (!mangaDynamicColorSchemes) {
      return () => {};
    }

    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.src = Mangas.getThumbnailUrl(manga);

    img.onload = () => {
      const isLargeImage = img.width > 600 && img.height > 600;

      Promise.all([
        Vibrant.from(img).getPalette(),
        new FastAverageColor().getColor(img, {
          algorithm: 'dominant',
          mode: isLargeImage ? 'speed' : 'precision',
          ignoredColor: [
            [255, 255, 255, 255, 75],
            [0, 0, 0, 255, 75],
          ],
        }),
      ]).then(([palette, averageColor]) => {
        if (
          !palette.Vibrant ||
          !palette.DarkVibrant ||
          !palette.LightVibrant ||
          !palette.LightMuted ||
          !palette.Muted ||
          !palette.DarkMuted
        ) {
          return;
        }

        setDynamicColor({
          ...palette,
          average: averageColor,
        } as TAppThemeContext['dynamicColor']);
      });
    };

    return () => {
      setDynamicColor(null);
    };
  }, []);

  const longPressBind = useLongPress(
    useCallback(
      (e: any) => {
        e.preventDefault();
        actionMenuPopupState.open(e);
      },
      [actionMenuPopupState],
    ),
  );

  return (
    <>
      <Stack
        sx={{
          position: 'relative',
          borderRadius: 1,
          overflow: 'hidden',
          backgroundColor: 'background.paper',
          width: '150px',
          maxHeight: 'fit-content',
          aspectRatio: MANGA_COVER_ASPECT_RATIO,
          flexShrink: 0,
          flexGrow: 0,
          [theme.breakpoints.up('lg')]: {
            width: '200px',
          },
          [theme.breakpoints.up('xl')]: {
            width: '300px',
          },
        }}
        {...longPressBind()}
      >
        <SpinnerImage
          src={Mangas.getThumbnailUrl(manga)}
          alt="Manga Thumbnail"
          onLoad={() => setIsImageReady(true)}
          imgStyle={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
        {isImageReady && (
          <Stack
            sx={{
              position: 'absolute',
              top: 0,
              bottom: 0,
              width: '100%',
              justifyContent: 'center',
              alignItems: 'center',
              opacity: 0,
              '&:hover': {
                background: 'rgba(0, 0, 0, 0.4)',
                cursor: 'pointer',
                opacity: 1,
              },
            }}
          >
            <IconButton
              {...bindTrigger(actionMenuPopupState)}
              sx={{
                position: 'absolute',
                top: 8,
                right: 8,
                color: 'primary.contrastText',
                background: 'rgba(0, 0, 0, 0.6)',
                '&:hover': {
                  background: 'rgba(0, 0, 0, 0.8)',
                },
              }}
            >
              <MoreVertIcon fontSize="small" />
            </IconButton>
          </Stack>
        )}
      </Stack>
      <Modal {...bindPopover(fullscreenPopupState)} sx={{ outline: 0 }}>
        <Stack
          onClick={() => fullscreenPopupState.close()}
          sx={{
            height: '100vh',
            p: 2,
            outline: 0,
            justifyContent: 'center',
            alignItems: 'center'
          }}
        >
          <SpinnerImage
            src={Mangas.getThumbnailUrl(manga)}
            alt="Manga Thumbnail"
            imgStyle={{ height: '100%', width: '100%', objectFit: 'contain' }}
          />
        </Stack>
      </Modal>
      <Menu {...bindMenu(actionMenuPopupState)}>
        {(onClose) => (
          <Fragment>
            <MenuItem
              onClick={() => {
                fullscreenPopupState.open();
                onClose();
              }}
              Icon={OpenInFullIcon}
              title="Expand"
            />
            <MenuItem
              onClick={() => {
                console.log('Change Thumbnail clicked');
                onClose();
              }}
              Icon={PhotoCameraIcon}
              title="Change Thumbnail"
            />
            <MenuItem
              onClick={() => {
                console.log('Reset Thumbnail clicked');
                onClose();
              }}
              Icon={RestoreIcon}
              title="Reset Thumbnail"
            />
          </Fragment>
        )}
      </Menu>
    </>
  );
};

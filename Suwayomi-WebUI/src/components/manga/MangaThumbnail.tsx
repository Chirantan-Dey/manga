import React, { useRef } from 'react';
import { useMutation } from '@apollo/client';
import { SET_CUSTOM_THUMBNAIL, RESET_THUMBNAIL } from '../../graphql/mutations';
import PopupState, { bindTrigger, bindMenu } from 'material-ui-popup-state';
import { useLongPress } from 'use-long-press';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';

interface MangaThumbnailProps {
  mangaId: Manga['id'];
  thumbnailUrl: string;
  onThumbnailChange?: () => void;
}

const MangaThumbnail: React.FC<MangaThumbnailProps> = ({
  mangaId,
  thumbnailUrl,
  onThumbnailChange
}) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  
  const [setCustomThumbnail] = useMutation(SET_CUSTOM_THUMBNAIL, {
    onCompleted: () => {
      if (onThumbnailChange) onThumbnailChange();
    }
  });
  
  const [resetThumbnail] = useMutation(RESET_THUMBNAIL, {
    onCompleted: () => {
      if (onThumbnailChange) onThumbnailChange();
    }
  });

  const bindLongPress = useLongPress(
    (event, meta) => {
      if (meta.context) {
        meta.context(event);
      }
    },
    { threshold: 500, captureEvent: true }
  );

  const handleSetCustom = () => {
    if (fileInputRef.current) {
      fileInputRef.current.click();
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      setCustomThumbnail({
        variables: {
          mangaId,
          thumbnail: file
        }
      });
    }
  };

  const handleReset = () => {
    resetThumbnail({ variables: { mangaId } });
  };


  return (
    <PopupState variant="popover" popupId="manga-thumbnail-menu">
      {(popupState) => (
        <div {...bindLongPress(popupState.open)} style={{ position: 'relative' }}>
          <div {...bindTrigger(popupState)}>
            <img
              src={thumbnailUrl}
              alt="Manga thumbnail"
              style={{ maxWidth: '100%', cursor: 'pointer' }}
            />
          </div>

          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileChange}
            accept="image/*"
            style={{ display: 'none' }}
          />

          <Menu {...bindMenu(popupState)}>
            <MenuItem onClick={() => { handleSetCustom(); popupState.close(); }}>
              Set custom thumbnail
            </MenuItem>
            <MenuItem onClick={() => { handleReset(); popupState.close(); }}>
              Reset to default
            </MenuItem>
          </Menu>
        </div>
      )}
    </PopupState>
  );
};

export default MangaThumbnail;
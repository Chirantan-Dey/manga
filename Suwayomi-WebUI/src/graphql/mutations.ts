import { gql } from '@apollo/client';

export const SET_CUSTOM_THUMBNAIL = gql`
  mutation SetCustomThumbnail($mangaId: Int!, $thumbnail: Upload!) {
    setCustomThumbnail(mangaId: $mangaId, thumbnail: $thumbnail)
  }
`;

export const RESET_THUMBNAIL = gql`
  mutation ResetThumbnail($mangaId: Int!) {
    resetThumbnail(mangaId: $mangaId)
  }
`;
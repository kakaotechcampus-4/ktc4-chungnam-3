// YouTube 링크 파서.

import type { LinkParser } from './index';

export const youtubeParser: LinkParser = {
  platform: 'YOUTUBE',
  matches(url: string): boolean {
    return false;
  },
};

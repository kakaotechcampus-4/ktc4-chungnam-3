// 서버 추출 결과를 앱 모델로 변환. 백엔드 계약 변경을 흡수하는 지점.

import type { YouTubeContentExtractionResultDto } from '../../../domain/extraction/extractionResult';
import type { Content } from '../../../domain/content';
import type { Place } from '../../../domain/place';

export function toContent(dto: YouTubeContentExtractionResultDto): Content {
  throw new Error('not implemented');
}

export function toPlaceCandidates(dto: YouTubeContentExtractionResultDto): Place[] {
  return [];
}

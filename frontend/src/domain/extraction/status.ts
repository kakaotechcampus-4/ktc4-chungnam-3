// 백엔드 extraction/type 의 enum 3개를 그대로 옮긴 리터럴 유니온.

export type ExtractionStatus = 'SUCCESS' | 'PARTIAL' | 'FAILED';

export type EvidenceSource =
  | 'VIDEO_AUDIO'
  | 'VIDEO_TEXT'
  | 'VIDEO_VISUAL'
  | 'TITLE'
  | 'DESCRIPTION';

export type FailureStage =
  | 'METADATA_FETCH'
  | 'VIDEO_ACCESS'
  | 'GEMINI_CALL'
  | 'RESPONSE_MAPPING';

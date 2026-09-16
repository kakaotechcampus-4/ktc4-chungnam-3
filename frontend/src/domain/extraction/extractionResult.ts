// 백엔드 extraction/dto 의 record 6개를 그대로 옮긴 서버 응답 타입.

import type { EvidenceSource, ExtractionStatus, FailureStage } from './status';

export interface EvidenceDto {
  source: EvidenceSource;
  detail: string;
}

export interface AnalysisMetadataDto {
  analysisVersion: string;
  modelName: string;
  promptVersion: string;
}

export interface FailureInfoDto {
  stage: FailureStage;
  httpStatus: number | null;
  errorType: string;
  message: string;
  extractedScope: string;
}

export interface PlaceCandidateDto {
  candidateId: string;
  name: string;
  branchName: string | null;
  regionHint: string;
  description: string;
  suggestedOrder: number | null;
  evidence: EvidenceDto[];
  uncertainties: string[];
}

export interface TemporalInfoDto {
  subject: string;
  originalText: string;
  relatedPlaceCandidateIds: string[];
  startDate: string | null;
  endDate: string | null;
  startTime: string | null;
  endTime: string | null;
  evidence: EvidenceDto[];
  uncertainties: string[];
}

export interface YouTubeContentExtractionResultDto {
  status: ExtractionStatus;
  analysisMetadata: AnalysisMetadataDto;
  summary: string;
  summaryUncertainties: string[];
  placeCandidates: PlaceCandidateDto[];
  temporalInfos: TemporalInfoDto[];
  failure: FailureInfoDto | null;
}

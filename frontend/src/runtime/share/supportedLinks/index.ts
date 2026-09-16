// 지원 플랫폼 파서 레지스트리. 플랫폼 추가는 여기에 등록한다.

export interface LinkParser {
  platform: string;
  matches(url: string): boolean;
}

export const parsers: LinkParser[] = [];

export function resolveParser(url: string): LinkParser | null {
  return null;
}

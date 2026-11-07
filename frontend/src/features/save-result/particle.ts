// "{장소}으로 저장" 의 조사. 02 · 02b · 02c 가 같이 쓴다.
// 받침이 있으면 "으로", 없거나 ㄹ 받침이면 "로". 마지막 글자가 한글이 아니면(영문 · 숫자 · 기호) 조사를 정할 수 없어
// 장소명 대신 "이곳으로"를 쓴다(디자인 세션 결정. "CAFE 9" → "이곳으로 저장").
const FALLBACK = "이곳으로";

export function withRo(word: string): string {
    const code = word.charCodeAt(word.length - 1) - 0xac00;
    if (Number.isNaN(code) || code < 0 || code > 0xd7a3 - 0xac00) {
        return FALLBACK;
    }
    const jong = code % 28;
    return jong === 0 || jong === 8 ? `${word}로` : `${word}으로`;
}

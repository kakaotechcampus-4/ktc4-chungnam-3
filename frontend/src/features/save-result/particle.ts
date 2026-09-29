// "{장소}으로 저장" 의 조사. 02 · 02b · 02c 가 같이 쓴다.
// 받침이 있으면 "으로", 없거나 ㄹ 받침이면 "로". 마지막 글자가 한글이 아니면 "(으)로".
export function withRo(word: string): string {
    const code = word.charCodeAt(word.length - 1) - 0xac00;
    if (code < 0 || code > 0xd7a3 - 0xac00) return `${word}(으)로`;
    const jong = code % 28;
    return jong === 0 || jong === 8 ? `${word}로` : `${word}으로`;
}

// Figma `C · Color` 컬렉션. 변수명 그대로.
export const colors = {
    bg: {
        screen: "#f6f1ee",
        surface: "#fffcfa",
        subtle: "#ece4e0",
        placeholder: "#ddd2cd",
        overlay: "#2a212673",
        fade: "#f6f1ee",
    },
    border: {
        default: "#ddd2cd",
    },
    text: {
        primary: "#2a2126",
        secondary: "#6a5d61",
        onSubtle: "#4d4145",
        onPrimary: "#fffcfa",
        disabled: "#a69a9e",
    },
    brand: {
        primary: "#6b4e7a",
        primaryPressed: "#5a3f68",
        primaryDisabled: "#e6daeb",
        primarySubtle: "#f4eef6",
    },
    icon: {
        default: "#4d4145",
        onPrimary: "#fffcfa",
        brand: "#6b4e7a",
        secondary: "#6a5d61",
    },
    status: {
        infoFg: "#515c6b",
        infoBg: "#e8eaee",
        warningFg: "#fffcfa",
        warningBg: "#6b4e7a",
        neutralFg: "#4d4145",
        neutralBg: "#ece4e0",
        dangerFg: "#8c3f3a",
        dangerBg: "#f5e4e1",
    },
    // 로그인 제공자 색. 제공자 디자인 가이드를 따른다(강조 색 하나 원칙의 예외).
    // 카카오 가이드 규정: 컨테이너 #FEE500, 심볼 #000000, 레이블 #000000 85%.
    provider: {
        kakaoContainer: "#fee500",
        kakaoSymbol: "#000000",
        kakaoLabel: "#000000d9",
    },
} as const;

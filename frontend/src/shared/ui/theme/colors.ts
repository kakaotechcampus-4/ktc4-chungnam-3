// Figma `C · Color` 컬렉션. 변수명 그대로.
export const colors = {
  bg: {
    screen: '#f6f1ee',
    surface: '#fffcfa',
    subtle: '#ece4e0',
    placeholder: '#ddd2cd',
    overlay: '#2a212673',
    fade: '#f6f1ee',
  },
  border: {
    default: '#ddd2cd',
  },
  text: {
    primary: '#2a2126',
    secondary: '#6a5d61',
    onSubtle: '#4d4145',
    onPrimary: '#fffcfa',
    disabled: '#a69a9e',
  },
  brand: {
    primary: '#6b4e7a',
    primaryPressed: '#5a3f68',
    primaryDisabled: '#e6daeb',
    primarySubtle: '#f4eef6',
  },
  icon: {
    default: '#4d4145',
    onPrimary: '#fffcfa',
    brand: '#6b4e7a',
  },
  status: {
    infoFg: '#515c6b',
    infoBg: '#e8eaee',
    warningFg: '#fffcfa',
    warningBg: '#6b4e7a',
    neutralFg: '#4d4145',
    neutralBg: '#ece4e0',
    dangerFg: '#8c3f3a',
    dangerBg: '#f5e4e1',
  },
} as const;

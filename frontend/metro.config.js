// expo-router가 @expo/cli의 optional peer로 node_modules에 설치되어
// Expo CLI가 @react-navigation import를 막는다. 앱은 expo-router를 쓰지 않는다.
// node_modules에서 expo-router가 사라지면 이 줄은 지워도 된다.
process.env.EXPO_ROUTER_DISABLE_RN_NAVIGATION_CHECK = '1';

const { getDefaultConfig } = require('expo/metro-config');

module.exports = getDefaultConfig(__dirname);

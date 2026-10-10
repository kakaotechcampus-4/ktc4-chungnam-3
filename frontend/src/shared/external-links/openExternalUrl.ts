// 웹 페이지(약관 등)를 연다. url 이 null 이면(페이지가 아직 없음) 아무것도 하지 않는다.
// 인앱 브라우저(expo-web-browser)를 쓰고, 불러오기나 실행이 실패하면 기본 브라우저(Linking)로 연다.
// expo-web-browser 는 여기서 처음 쓸 때 불러온다(없는 개발 빌드에서 import 만으로 앱이 멈추지 않게).
// 누름 처리에서 부르므로 예외를 던지지 않는다.
import { Linking } from "react-native";

type WebBrowserModule = typeof import("expo-web-browser");

export async function openExternalUrl(url: string | null): Promise<void> {
    if (url == null) return;
    try {
        const webBrowser = require("expo-web-browser") as WebBrowserModule;
        await webBrowser.openBrowserAsync(url);
        return;
    } catch {
        // 아래에서 기본 브라우저로 연다.
    }
    try {
        await Linking.openURL(url);
    } catch {
        console.warn("웹 페이지를 열지 못했습니다.");
    }
}

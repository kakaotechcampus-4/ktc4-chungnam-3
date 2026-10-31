// 알림 채널 정의와 생성. 저장 알림과 제안 알림을 분리한다(지금은 근처 알림 하나).
// 앱(App 마운트)이 켤 때마다 한 번 부른다. 같은 값으로 다시 만들면 아무 일도 없다.
// 서버가 channelId 없이 보내는 FCM 은 app.json 의 expo-notifications defaultChannel 로 이 채널에 들어온다(앱이 뒤에 있을 때).
import { ensureNotificationChannel } from "../../shared/permissions/notifications";

// 바꾸지 않는다. app.json 의 expo-notifications 플러그인 "defaultChannel" 과 같아야 한다(app.config.ts 상단 주석).
export const NEARBY_CHANNEL_ID = "nearby";

export async function ensureChannels(): Promise<void> {
    await ensureNotificationChannel({
        id: NEARBY_CHANNEL_ID,
        name: "근처 알림",
        description: "저장한 장소 근처에 오면 알려줘요",
        importance: "default",
    });
}

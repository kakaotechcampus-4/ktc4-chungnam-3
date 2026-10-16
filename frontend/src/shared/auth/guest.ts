// 게스트 로그인(MVP). 설치 id 를 자격 증명으로 쓴다(백엔드 POST /api/auth/guest { installationId }).
// 설치 id 는 처음 필요할 때 무작위 UUID v4 로 만들어 안전한 저장소에 둔다. 이 값으로 그 게스트의 세션을 받을 수 있으므로
// 로그 · 오류 · 경고에 남기지 않는다. 난수는 expo-crypto 의 randomUUID(안드로이드 java.util.UUID, 보안 난수)다.
// expo-crypto 는 처음 쓸 때 불러온다(없는 개발 빌드에서 import 만으로 앱이 멈추지 않게).
// 목 모드(API 주소 없음)에서는 설치 id 를 만들지 않는다. MOCK_SCENARIO.loginResult 가 failed 면 00a-err 를 본다.
import { isApiConfigured } from "../api/config";
import { MOCK_SCENARIO } from "../api/mock";
import {
    isSecureStoreAvailable,
    readSecureItem,
    writeSecureItem,
} from "../storage/secureStore";
import type { ProviderSignInResult } from "./providers";

const INSTALLATION_ID_KEY = "remembrall.installation-id";
const MOCK_DELAY_MS = 600;

type CryptoModule = typeof import("expo-crypto");
let cryptoModule: CryptoModule | null | undefined;

function loadCrypto(): CryptoModule | null {
    if (cryptoModule === undefined) {
        try {
            cryptoModule = require("expo-crypto") as CryptoModule;
        } catch {
            cryptoModule = null;
            console.warn(
                "expo-crypto 네이티브 모듈이 없는 빌드입니다. 설치 id 를 만들 수 없어 시작하지 않습니다. 개발 빌드를 다시 만드세요.",
            );
        }
    }
    return cryptoModule;
}

// 저장소가 없는 빌드에서는 이번 실행 동안만 메모리에 둔다(다음 실행은 새 게스트).
let installationId: string | null = null;

async function getInstallationId(): Promise<string> {
    if (installationId != null) return installationId;
    const stored = await readSecureItem(INSTALLATION_ID_KEY);
    if (stored != null) {
        installationId = stored;
        return stored;
    }
    const module = loadCrypto();
    if (module == null) {
        throw new Error("설치 id 를 만들 수 없습니다(expo-crypto 없음).");
    }
    const created = module.randomUUID();
    installationId = created;
    if (isSecureStoreAvailable()) {
        await writeSecureItem(INSTALLATION_ID_KEY, created);
    }
    return created;
}

export async function signInAsGuest(): Promise<ProviderSignInResult> {
    if (!isApiConfigured) {
        await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
        if (MOCK_SCENARIO.loginResult === "failed") {
            throw new Error("mock guest login failed");
        }
        return { type: "success", credential: "" };
    }
    return { type: "success", credential: await getInstallationId() };
}

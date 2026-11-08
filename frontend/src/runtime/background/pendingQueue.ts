// 저장 보내기 대기열. 공유받은 링크를 보내지 못했을 때(네트워크 실패 · 세션 없음 · 온보딩 중) 기기에 남겨 두고 나중에 다시 보낸다.
// 같은 videoId 는 한 번만 둔다. 최대 MAX_ITEMS 개이고 넘치면 가장 오래된 것부터 버린다.
// 파일 하나(Paths.document/pending-saves.json)에 두어 앱을 껐다 켜도 남는다. 링크는 비밀이 아니라 안전한 저장소를 쓰지 않는다.
// expo-file-system 은 처음 쓸 때 불러온다. 없거나 읽기 · 쓰기에 실패하면 이번 실행 동안 메모리에만 둔다.
// drain 은 앞에서부터 하나씩 보낸다. 성공(201 · 200)과 다시 보내도 소용없는 4xx 는 지우고,
// 네트워크 실패 · 401 · 5xx 는 남기고 이번 drain 을 멈춘다. 재시도 횟수 제한과 백오프는 없다.
// 목 모드에서도 같은 규칙이다(savePersonalSave 가 목 저장소를 쓴다).
import { ApiError } from "../../shared/api/client";
import { savePersonalSave } from "../../shared/api/personalSaves";
import type { SharedLink } from "../share/supportedLinks";

// url 은 정규화된 링크, queuedAt 은 넣은 시각(ISO-8601 UTC).
export type PendingSave = { url: string; videoId: string; queuedAt: string };

// sent: 보내서 지움. dropped: 다시 보내도 소용없어 지움. remaining: 아직 남음.
export type DrainResult = { sent: number; remaining: number; dropped: number };

type FileSystemModule = typeof import("expo-file-system");

const FILE_NAME = "pending-saves.json";
const MAX_ITEMS = 20;

let fileSystem: FileSystemModule | null | undefined;
// 처음 읽은 뒤로는 이 배열 하나가 기준이다. 바꿀 때마다 파일에 통째로 쓴다.
let queue: Promise<PendingSave[]> | undefined;
let draining: Promise<DrainResult> | undefined;

export async function enqueue(link: SharedLink): Promise<void> {
    const items = await loadQueue();
    if (items.some((item) => item.videoId === link.videoId)) return;
    items.push({
        url: link.url,
        videoId: link.videoId,
        queuedAt: new Date().toISOString(),
    });
    if (items.length > MAX_ITEMS) items.splice(0, items.length - MAX_ITEMS);
    writeQueue(items);
}

// 이미 돌고 있으면 새로 돌지 않고 그 결과를 같이 받는다.
export function drain(): Promise<DrainResult> {
    draining ??= sendAll().finally(() => {
        draining = undefined;
    });
    return draining;
}

export async function getPendingCount(): Promise<number> {
    return (await loadQueue()).length;
}

async function sendAll(): Promise<DrainResult> {
    const items = await loadQueue();
    let sent = 0;
    let dropped = 0;
    // 보내는 동안 enqueue 가 뒤에 붙인 항목도 이어서 보낸다.
    while (items.length > 0) {
        const item = items[0];
        const outcome = await sendOne(item);
        if (outcome === "keep") break;
        if (outcome === "sent") sent += 1;
        else dropped += 1;
        // 보내는 동안 넘쳐서 이미 버려졌을 수 있어 videoId 로 찾는다.
        const index = items.findIndex(
            (pending) => pending.videoId === item.videoId,
        );
        if (index >= 0) items.splice(index, 1);
        writeQueue(items);
    }
    return { sent, remaining: items.length, dropped };
}

async function sendOne(item: PendingSave): Promise<"sent" | "drop" | "keep"> {
    try {
        await savePersonalSave(item.url);
        return "sent";
    } catch (error) {
        return isPermanentFailure(error) ? "drop" : "keep";
    }
}

// 다시 보내도 같은 결과인 실패. 401(세션)을 뺀 4xx 다. 응답 없음 · 5xx · 그 밖의 예외는 다시 보낸다.
function isPermanentFailure(error: unknown): boolean {
    if (!(error instanceof ApiError) || error.status == null) return false;
    return error.status >= 400 && error.status < 500 && error.status !== 401;
}

function loadQueue(): Promise<PendingSave[]> {
    queue ??= readQueue();
    return queue;
}

async function readQueue(): Promise<PendingSave[]> {
    try {
        const file = queueFile();
        if (file == null || !file.exists) return [];
        return parseQueue(await file.text());
    } catch {
        // 깨진 파일은 빈 대기열로 시작한다. 다음 쓰기가 덮어쓴다.
        return [];
    }
}

function writeQueue(items: readonly PendingSave[]): void {
    try {
        const file = queueFile();
        if (file == null) return;
        if (!file.exists) file.create();
        file.write(JSON.stringify(items));
    } catch {
        // 쓰지 못해도 메모리의 대기열은 그대로다. 이번 실행 동안은 다시 보낼 수 있다.
    }
}

function parseQueue(text: string): PendingSave[] {
    const value: unknown = JSON.parse(text);
    if (!Array.isArray(value)) return [];
    return value.filter(isPendingSave).slice(-MAX_ITEMS);
}

function isPendingSave(value: unknown): value is PendingSave {
    return (
        typeof value === "object" &&
        value != null &&
        "url" in value &&
        typeof value.url === "string" &&
        "videoId" in value &&
        typeof value.videoId === "string" &&
        "queuedAt" in value &&
        typeof value.queuedAt === "string"
    );
}

function queueFile() {
    if (fileSystem === undefined) {
        try {
            fileSystem = require("expo-file-system") as FileSystemModule;
        } catch {
            fileSystem = null;
            console.warn(
                "expo-file-system 네이티브 모듈이 없는 빌드입니다. 저장 대기열을 이번 실행 동안만 메모리에 둡니다.",
            );
        }
    }
    if (fileSystem == null) return null;
    return new fileSystem.File(fileSystem.Paths.document, FILE_NAME);
}

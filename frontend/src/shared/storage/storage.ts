// 로컬 영속 저장소. 키 하나를 파일 하나(Paths.document/{key}.json)에 JSON 으로 둔다. 비밀이 아닌 값용이다(비밀은 secureStore).
// expo-file-system 은 처음 쓸 때 불러온다. 없거나 읽기 · 쓰기에 실패하면 읽기는 null, 쓰기 · 지우기는 아무것도 하지 않는다
// (부르는 쪽이 메모리에 둔 값으로 이번 실행을 이어간다). 깨진 JSON 도 null 이다. 값의 모양은 부르는 쪽이 확인한다.
// 쓰기 · 지우기는 동기 API 라 JS 안에서 서로 끼어들지 않는다. 같은 키를 여러 곳에서 쓰지 않는다.
type FileSystemModule = typeof import("expo-file-system");

let fileSystem: FileSystemModule | null | undefined;

function file(key: string) {
    if (fileSystem === undefined) {
        try {
            fileSystem = require("expo-file-system") as FileSystemModule;
        } catch {
            fileSystem = null;
            console.warn(
                "expo-file-system 네이티브 모듈이 없는 빌드입니다. 저장한 값을 이번 실행 동안만 메모리에 둡니다.",
            );
        }
    }
    if (fileSystem == null) return null;
    return new fileSystem.File(fileSystem.Paths.document, `${key}.json`);
}

export async function read(key: string): Promise<unknown> {
    try {
        const target = file(key);
        if (target == null || !target.exists) return null;
        return JSON.parse(await target.text());
    } catch {
        return null;
    }
}

export function write(key: string, value: unknown): void {
    try {
        const target = file(key);
        if (target == null) return;
        if (!target.exists) target.create();
        target.write(JSON.stringify(value));
    } catch {
        // 쓰지 못해도 부르는 쪽의 메모리 값은 그대로다.
    }
}

export function remove(key: string): void {
    try {
        const target = file(key);
        if (target != null && target.exists) target.delete();
    } catch {
        // 지우지 못하면 다음 쓰기가 덮어쓴다.
    }
}

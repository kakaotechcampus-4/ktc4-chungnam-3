// expo-task-manager 지연 로더와 지오펜스 태스크 이름. 이 모듈은 불러오는 순간 네이티브 모듈을 찾고(requireNativeModule)
// 없으면 예외를 던지므로 처음 쓸 때 try 로 불러온다. 없는 빌드(Expo Go · 이 모듈 전에 만든 개발 빌드)에서는 null 이다.
// 경고는 태스크를 정의할 때(geofenceTask) 한 번만 낸다.
type TaskManagerModule = typeof import("expo-task-manager");

export const GEOFENCE_TASK = "remembrall-geofence";

let taskManager: TaskManagerModule | null | undefined;

export function loadTaskManager(): TaskManagerModule | null {
    if (taskManager === undefined) {
        try {
            taskManager = require("expo-task-manager") as TaskManagerModule;
        } catch {
            taskManager = null;
        }
    }
    return taskManager;
}

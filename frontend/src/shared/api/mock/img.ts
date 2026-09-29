// 목 썸네일 URL(picsum 고정 id, 9:16). brokenThumbnails 면 존재하지 않는 주소(.invalid)를 돌려준다.
import { MOCK_SCENARIO } from "./scenario";

export const img = (id: number) =>
    MOCK_SCENARIO.brokenThumbnails
        ? `https://broken.invalid/${id}.jpg`
        : `https://picsum.photos/id/${id}/360/640`;

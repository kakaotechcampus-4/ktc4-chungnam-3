// Figma 변수 아님, 노드 실측값. 변수에 바인딩되지 않은 치수를 컴포넌트별로 둔다.
export const metrics = {
    // C/BottomNav 2101:419. 높이 64 = 위아래 8 + pill(4 + 아이콘 22 + 4) + 간격 2 + 라벨 16.
    bottomNav: {
        iconSize: 22,
        labelGap: 2,
    },
    // 01 · 06 · 07 · 07b Header (2061:762). 높이 36, 톱니 버튼 40 → 사방 4 씩 48dp.
    screenHeader: {
        height: 36,
        locationIconSize: 16,
        settingsButtonSize: 40,
        settingsIconSize: 22,
        settingsHitSlop: { top: 4, bottom: 4, left: 4, right: 4 },
    },
    // C/Thumb 2054:102. 9:16 세로. 재생 배지(source/Shorts 2054:104)와 그 안의 Play 위치.
    thumb: {
        aspectRatio: 9 / 16,
        sourceSize: 20,
        sourceInset: 6,
        playSize: 12,
        playOffsetLeft: 4.5,
        playOffsetTop: 4,
    },
    // C/Badge 2054:150.
    badge: {
        paddingVertical: 3,
    },
    // C/Chip 2054:139. 높이 size.chip 32 → 상하 8 씩 48dp.
    // Figma 는 테두리를 크기에 넣지 않는다. RN 은 테두리가 안쪽에 들어가므로 좌우 padding 에서 테두리만큼 뺀다.
    chip: {
        paddingHorizontal: 14,
        hitSlop: { top: 8, bottom: 8 },
    },
    // C/Card/Nearby 2054:120.
    nearbyCard: {
        thumbWidth: 112,
        nowPaddingHorizontal: 10,
        nowPaddingVertical: 3,
        summaryMaxLines: 3,
    },
    // C/Card/Memory 2054:107.
    memoryCard: {
        thumbWidth: 72,
        summaryMaxLines: 2,
    },
    // 01 근처 2061:760.
    // 위치 줄이 헤더로 올라가 소개 영역 위 padding 이 16 이 됐다 (01 Intro 2061:764).
    proposal: {
        introPaddingTop: 16,
        introGap: 6,
        listPaddingTop: 16,
    },
    // 06 근처 빈 상태 2061:881. 링크 높이 20 → 상하 14 씩 48dp.
    // stack: Figma 360 폭 절대좌표를 썸네일 중심 기준으로 바꾼 값. dx 는 화면 가운데로부터의 거리.
    nearbyEmpty: {
        linkIconSize: 16,
        linkHitSlop: { top: 14, bottom: 14 },
        stackHeight: 300,
        stackThumbWidth: 104,
        stack: [
            { dx: -47.1, top: 63, rotate: "9deg" },
            { dx: 50.9, top: 41, rotate: "-7deg" },
            { dx: 2.4, top: 40.9, rotate: "1deg" },
        ],
    },
    // 07 전체 기억 셀 2061:911.
    albumCell: {
        labelGap: 6,
        badgeInset: 6,
    },
    // 07b 전체 기억 빈 상태 2061:956. frames: 360 폭 절대좌표를 프레임 중심 기준으로 바꾼 값.
    archiveEmpty: {
        introPaddingTop: 40,
        framesHeight: 300,
        frameWidth: 84,
        frameHeight: 150,
        frames: [
            { dx: -86, top: 70 },
            { dx: 0, top: 50 },
            { dx: 86, top: 70 },
        ],
    },
    // 02~05 저장 결과 2061:784. 닫기 아이콘 24 → 사방 12 씩 48dp.
    saveResult: {
        closeHitSlop: { top: 12, bottom: 12, left: 12, right: 12 },
        sourceGap: 16,
        thumbWidth: 96,
        infoGap: 6,
    },
    // 02 장소 후보 2061:799 · 2061:804.
    placeOption: {
        paddingHorizontal: 16,
        paddingVertical: 14,
        checkSize: 20,
    },
    // 02 "여기 없어요, 직접 찾을게요" 2061:808. 높이 44 → 상하 2 씩 48dp.
    directOption: {
        paddingHorizontal: 16,
        hitSlop: { top: 2, bottom: 2 },
    },
    // 08 장소 상세 2061:971. 시트는 지도 위로 38 겹친다(top 292). 뒤로가기 38 → 사방 5 씩 48dp.
    contentDetail: {
        mapHeight: 330,
        sheetOverlap: 38,
        backLeft: 16,
        backIconSize: 22,
        backHitSlop: { top: 5, bottom: 5, left: 5, right: 5 },
        sheetPaddingTop: 10,
        sheetPaddingBottom: 24,
        sheetGap: 16,
        handleWidth: 36,
        handleHeight: 4,
        headGap: 16,
        thumbWidth: 72,
        noteGap: 6,
        tagGap: 6,
        tagPaddingVertical: 2,
        sourcePaddingLeft: 14,
    },
    // 11 설정 2103:626. 뒤로가기 아이콘 24 → 사방 12 씩 48dp.
    settings: {
        backHitSlop: { top: 12, bottom: 12, left: 12, right: 12 },
        rowPaddingHorizontal: 16,
        rowPaddingVertical: 14,
    },
    // C/Switch 2101:424. 손잡이는 트랙 안쪽 3 떨어져 있다. 켜짐·꺼짐 전환은 150ms 슬라이드.
    settingsSwitch: {
        width: 48,
        height: 28,
        knobSize: 22,
        knobInset: 3,
        durationMs: 150,
    },
    // 12 저장물 없음 2105:682. 빈 썸네일 자리 96 × 9:16, 닫기 · 뒤로 아이콘 24 → 사방 12 씩 48dp.
    notFound: {
        thumbWidth: 96,
        closeHitSlop: { top: 12, bottom: 12, left: 12, right: 12 },
    },
    // 03 분석 중 스켈레톤 2061:831.
    skeleton: {
        padding: 16,
        cards: [
            [
                { width: 140, height: 14 },
                { width: 90, height: 10 },
            ],
            [
                { width: 110, height: 14 },
                { width: 60, height: 10 },
            ],
        ],
    },
} as const;

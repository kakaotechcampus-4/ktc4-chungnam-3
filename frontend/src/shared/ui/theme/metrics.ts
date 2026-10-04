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
    // 02 장소 후보 2061:799 · 2061:804. 높이 70. padding 은 Figma 값(테두리 제외)이고 코드에서 테두리 두께를 뺀다.
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
    // 08 장소 상세 2061:971. 뒤로가기 38 → 사방 5 씩 48dp.
    // 시트 높이는 고정값이 아니라 내용 높이다(08 · 08b 측정). 기본 지점에서 메모는 2줄로 줄인다.
    contentDetail: {
        backLeft: 16,
        backIconSize: 22,
        backHitSlop: { top: 5, bottom: 5, left: 5, right: 5 },
        sheetPaddingBottom: 24,
        sheetGap: 16,
        headGap: 16,
        thumbWidth: 72,
        noteGap: 6,
        tagGap: 6,
        tagPaddingVertical: 2,
        // 원본 영상 행 왼쪽 padding. Figma 값(테두리 제외)이고 코드에서 테두리 두께를 뺀다. 행 높이 64.
        sourcePaddingLeft: 14,
        noteCollapsedLines: 2,
        // 지도: 현재 위치 → 장소 점선 경로(text/secondary, 두께 2 는 stroke 토큰에 없음, 점선 4 · 4).
        // 지도 위쪽은 뒤로가기(38) 아래 md 부터 쓴다. 현재 위치 · 장소를 맞출 때 좌우 · 아래 여백은 구현값이다.
        routeWidth: 2,
        routeDashPattern: [4, 4],
        backSize: 38,
        mapFitPadding: 48,
    },
    // 끌 수 있는 시트 핸들 (08 2061:993 · 10b 2103:573). 시트 위 10, 36 × 4.
    sheetHandle: {
        paddingTop: 10,
        width: 36,
        height: 4,
    },
    // 지도 사진 핀 (10 2103:471 · 2103:439 · 2103:449). 9:16, 폭과 테두리는 fade 로 정한다.
    // 이름표(10b 2103:570)는 핀 아래 7 떨어진다. 이름표 높이 = captionMeta 16 + 위아래 3.
    // shadowMargin: 그림자 있는 핀의 비트맵 여백 = elevationLow blur 12 + offsetY 2.
    // redrawDelayMs: 다음 프레임에 한 번 찍은 뒤 다시 한 번 찍는 지연. 뷰가 덜 그려진 채 굳지 않게 한다. 구현값이다.
    photoMarker: {
        recent: { width: 40, borderWidth: 3 },
        weeks: { width: 26, borderWidth: 2 },
        months: { width: 22, borderWidth: 2 },
        labelGap: 7,
        labelPaddingHorizontal: 8,
        labelPaddingVertical: 3,
        shadowMargin: 14,
        redrawDelayMs: 100,
    },
    // 현재 위치 (10 2103:469 · 2103:470). 바깥 원은 status/info-fg 15%, 안쪽 점은 bg/surface 테두리 3.
    currentLocation: {
        haloSize: 44,
        haloOpacity: 0.15,
        dotSize: 14,
        dotBorderWidth: 3,
    },
    // 10 지도 탭 2103:427 · 10b 2103:525.
    // 필터는 상태바 아래 8, 좌우 16. 내 위치 버튼 44(안쪽 11 + 아이콘 22)는 오른쪽 16, 하단 바 위 20, 시트 위 16.
    // 요약 시트: 위 10 + 핸들 4 + 간격 16 + 요약(썸네일 56 × 9:16) + 아래 20.
    mapView: {
        filtersPaddingHorizontal: 16,
        locateRight: 16,
        locateBottom: 20,
        locateSheetGap: 16,
        locatePadding: 11,
        locateIconSize: 22,
        locateHitSlop: { top: 2, bottom: 2, left: 2, right: 2 },
        sheetGap: 16,
        sheetPaddingBottom: 20,
        summaryGap: 16,
        summaryThumbWidth: 56,
        summaryChevronSize: 20,
    },
    // 상단 바 (11 설정 2103:633 · 02c 2105:593 · 12 2105:682). 뒤로 · 닫기 아이콘 24 → 사방 12 씩 48dp.
    topBar: {
        iconHitSlop: { top: 12, bottom: 12, left: 12, right: 12 },
    },
    // C/SearchField 2104:567. 지우기 아이콘 18 → 사방 15 씩 48dp.
    // inputPadding 은 Android TextInput 기본 여백을 없애 높이 48 안에서 글자를 가운데 둔다.
    // paddingLeft 는 Figma 값(테두리 제외)이고 코드에서 테두리 두께를 뺀다.
    searchField: {
        paddingLeft: 14,
        iconSize: 20,
        clearHitSlop: { top: 15, bottom: 15, left: 15, right: 15 },
        inputPadding: 0,
    },
    // 02c · 02d 장소 직접 찾기 2105:586 · 2105:642.
    placeSearch: {
        resultsPaddingTop: 16,
        emptyPaddingTop: 40,
    },
    // 11 설정 2103:626.
    settings: {
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
    // 12 저장물 없음 2105:682. 빈 썸네일 자리 96 × 9:16. 상단 바는 topBar 를 쓴다.
    notFound: {
        thumbWidth: 96,
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

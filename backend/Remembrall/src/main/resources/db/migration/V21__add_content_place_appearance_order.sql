-- 영상 안에서 이 장소가 나온 순서(0부터). 저장 목록의 대표 장소명을 "영상에 먼저 나온 장소"로 고르는 데 쓴다.
-- 기존 행과 되묻기 답변으로 나중에 확정된 장소는 NULL(대표 선정에서 뒤로 밀림).
ALTER TABLE content_place
    ADD COLUMN appearance_order INTEGER CHECK (appearance_order >= 0);

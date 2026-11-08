# 릴리스 도구 검증 — 2026-11-08

- `node --test test/external/run.test.mjs`: 7개 통과.
- `.github/deploy/test.sh`: 네트워크 차단·호스트 읽기 전용 마운트의 일회용 컨테이너에서 9개 통과.
- 배포 모의 범위: DB 백업 실패, 비활성 포트 점유, 새 버전 불량, Nginx 설정 실패,
  외부 상태 확인 실패, 정상 전환·기존 서비스 종료, 작업 미완료 시 기존 프로세스 유지,
  바이너리 체크섬 불일치, 운영 모델 불일치.
- actionlint, Bash 문법, 외부 Bruno 요청 11개 파싱·JavaScript 문법 확인 통과.
- 테스트는 가짜 systemctl/nginx/docker/curl 명령을 사용했다. 운영 서비스·Nginx·DB는 수정하지 않았다.

아직 수행하지 않은 항목: 실제 API 키로 전체 external gate 실행, GitHub Actions 실제 release 실행,
SSH/SCP 실제 artifact 배포, 서버 bootstrap 적용, 실제 Nginx/systemd 전환·드레인 검증.
GitHub external-api 환경의 필수 API Secret 6개와 모델·영상 URL 변수, main 브랜치 제한은 등록을 확인했다.
Secret의 실제 값과 API 호출 성공 여부는 아직 검증하지 않았다. SSH 배포 키와 운영 배포 준비는 미완료이며,
BACKEND_DEPLOY_ENABLED는 사용자 확인 기준 false로 등록했고 아직 켜지 않았다.
이 기록을 실제 외부 연동이나 운영 무중단 배포 성공 결과로 사용하지 않는다.

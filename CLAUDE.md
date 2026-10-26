# CLAUDE.md

개인용 Olauncher fork. 작업 전에 아래 규칙을 따른다.

## 사용 환경
- 기기: Samsung Galaxy Tab, 가로 화면, One UI
- 시스템 언어: 독일어

## 규칙
- APK 빌드와 서명은 `.github/workflows/build.yml` 이 담당한다. 이 파일과 서명 방식은 건드리지 않는다.
- `applicationId`(`app.olauncher.custom`)와 `namespace`(`app.olauncher`)는 바꾸지 않는다.
- 요청받은 것만 최소 범위로 수정한다. 관련 없는 리팩터링은 하지 않는다.
- 새 권한이나 새 라이브러리가 필요하면 먼저 물어본다.
- 작업이 끝나면 한국어로 짧게 정리한다: 바뀐 것 / 태블릿에서 확인하는 법 / 되돌리는 법
- 모든 변경은 `CUSTOM_CHANGES.md` 에 날짜와 한 줄 요약으로 기록한다.

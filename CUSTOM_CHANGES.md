# Custom Changes

- 2026-10-26: 초기 세팅 — applicationId 를 app.olauncher.custom 으로, app_name 을 "My Launcher" 로 변경, CLAUDE.md 와 CUSTOM_CHANGES.md 추가
- 2026-10-26: 접근성 서비스 packageNames 를 app.olauncher.custom(.debug) 로 갱신 — 두 번 탭 화면 잠금 복구
- 2026-10-26: .github/workflows/build.yml 추가 — release APK 빌드 후 Secrets 의 고정 키스토어로 서명, MyLauncher.apk 아티팩트 업로드
- 2026-10-26: build.yml 을 master push 시 빌드·서명 후 Release "latest" 에 launcher.apk 로 올리도록 변경 (고정 다운로드 주소)
- 2026-10-26: 설정에 테마 JSON 추가 — 시계 · 홈 앱 · 앱 서랍의 크기 · 글꼴 · 정렬 · 여백 · 색 · 그림자를 빌드 없이 변경 (키 목록 THEME.md)
- 2026-10-27: 홈에서 왼쪽으로 스와이프하면 위젯 화면 열기 — 위젯 추가 · 제거, 오른쪽 스와이프/뒤로로 복귀 (기존 왼쪽 스와이프 앱 설정은 숨김)
- 2026-10-27: 위젯 화면을 오른쪽 스와이프로 변경 (왼쪽 스와이프는 다시 카메라), 편집 모드에 위젯 순서(↑↓) · 너비/높이 조절 추가

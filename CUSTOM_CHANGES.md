# Custom Changes

- 2026-10-26: 초기 세팅 — applicationId 를 app.olauncher.custom 으로, app_name 을 "My Launcher" 로 변경, CLAUDE.md 와 CUSTOM_CHANGES.md 추가
- 2026-10-26: 접근성 서비스 packageNames 를 app.olauncher.custom(.debug) 로 갱신 — 두 번 탭 화면 잠금 복구
- 2026-10-26: .github/workflows/build.yml 추가 — release APK 빌드 후 Secrets 의 고정 키스토어로 서명, MyLauncher.apk 아티팩트 업로드
- 2026-10-26: build.yml 을 master push 시 빌드·서명 후 Release "latest" 에 launcher.apk 로 올리도록 변경 (고정 다운로드 주소)

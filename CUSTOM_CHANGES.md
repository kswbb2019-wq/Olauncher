# Custom Changes

- 2026-10-26: 초기 세팅 — applicationId 를 app.olauncher.custom 으로, app_name 을 "My Launcher" 로 변경, CLAUDE.md 와 CUSTOM_CHANGES.md 추가
- 2026-10-26: 접근성 서비스 packageNames 를 app.olauncher.custom(.debug) 로 갱신 — 두 번 탭 화면 잠금 복구
- 2026-10-26: .github/workflows/build.yml 추가 — release APK 빌드 후 Secrets 의 고정 키스토어로 서명, MyLauncher.apk 아티팩트 업로드
- 2026-10-26: build.yml 을 master push 시 빌드·서명 후 Release "latest" 에 launcher.apk 로 올리도록 변경 (고정 다운로드 주소)
- 2026-10-26: 설정에 테마 JSON 추가 — 시계 · 홈 앱 · 앱 서랍의 크기 · 글꼴 · 정렬 · 여백 · 색 · 그림자를 빌드 없이 변경 (키 목록 THEME.md)
- 2026-10-27: 홈에서 왼쪽으로 스와이프하면 위젯 화면 열기 — 위젯 추가 · 제거, 오른쪽 스와이프/뒤로로 복귀 (기존 왼쪽 스와이프 앱 설정은 숨김)
- 2026-10-27: 위젯 화면을 오른쪽 스와이프로 변경 (왼쪽 스와이프는 다시 카메라), 편집 모드에 위젯 순서(↑↓) · 너비/높이 조절 추가
- 2026-10-27: 위젯이 "Widget konnte nicht hinzugefügt werden" 로 표시되던 문제 수정 — AppCompat 대신 시스템 테마 컨텍스트로 위젯 표시
- 2026-10-27: 위젯 화면에 런처 자체 월 달력 추가 — 기기 캘린더(삼성 캘린더) 일정 표시, 날짜/일정 탭은 삼성 캘린더로 열기, 길게 눌러 새 일정 (READ_CALENDAR 권한 추가)
- 2026-10-27: 위젯 크기를 위젯이 정한 최소/최대 · 크기 조절 방향 범위 안으로 제한, 기본 크기는 위젯 권장 크기 ("Inhalt kann nicht angezeigt werden" 대응)
- 2026-10-27: 삼성 위젯은 위젯이 정한 기본 크기(minWidth × minHeight)로 추가, 편집 모드에 Standardgröße(기본 크기로 되돌리기) 추가
- 2026-10-30: 홈 화면 폴더 추가 — 홈 슬롯 앱 선택 목록의 "+ Ordner erstellen", 폴더 화면(실행 · 길게 눌러 빼기 · 앱 추가 · 이름 바꾸기 · 삭제), Prefs에 JSON 저장
- 2026-11-16: 할 일 1/3 데이터와 동기화 — data/tasks.json(63개), GitHub Contents API 양방향 동기화(updatedAt 병합, tombstone), 설정 > Aufgaben 에 저장소 · 토큰 · 상태, data/ 만 바뀐 커밋은 빌드 안 함
- 2026-11-16: 할 일 2/3 표시와 편집 — 홈 날짜 아래 "Heute ›" 오늘 할 일(탭하면 완료), 날짜별 전체 목록(+ 추가, 길게 눌러 수정 · 삭제), 홈 표시 켜기/끄기, 홈 복귀 시 15분 간격 동기화
- 2026-11-16: 할 일 3/3 알림 — 할 일 시각 또는 기본 알림 시각(08:00)에 그날 미완료 할 일 알림, 설정에서 켜기/끄기 · 시각, POST_NOTIFICATIONS · RECEIVE_BOOT_COMPLETED 권한, 재부팅 · 시각 변경 후 다시 예약

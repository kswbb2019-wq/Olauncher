![Olauncher](https://repository-images.githubusercontent.com/278638069/db0acb80-661b-11eb-803e-926cae5dccb4)


# Olauncher | Minimal AF Launcher
AF stands for Ad-Free! :D

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
    alt="Get it on Play Store"
    height="80" align="middle">](https://play.google.com/store/apps/details?id=app.olauncher)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80" align="middle">](https://f-droid.org/packages/app.olauncher)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png"
    alt="Get it on IzzyOnDroid"
    height="54" hspace="13" align="middle">](https://apt.izzysoft.de/packages/app.olauncher)

#### Install using [Play Store,](https://play.google.com/store/apps/details?id=app.olauncher) [F-Droid,](https://f-droid.org/packages/app.olauncher) [IzzyOnDroid](https://apt.izzysoft.de/packages/app.olauncher) or the [latest APK.](https://github.com/tanujnotes/Olauncher/releases/)

To maintain the simplicity of the launcher, a few niche features are available but hidden.

Please check out the **[About](https://tanujnotes.substack.com/p/olauncher-minimal-af-launcher?utm_source=github)** page in the Olauncher settings for a complete list of features and **FAQs**.

##

License: [GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html)

Developer: [@tanujnotes](https://x.com/tanujnotes)

##

### Other apps from team Olauncher:

- [Pro Launcher](https://play.google.com/store/apps/details?id=app.prolauncher) - Pro version of Olauncher with extra features like widgets, weather, folders, etc.

- [Note to Self](https://play.google.com/store/apps/details?id=com.makenotetoself) - Free and [open source](https://github.com/jeerovan/ntsapp) notes app with chat like interface and end-to-end encryption.

- [Pentastic](https://play.google.com/store/apps/details?id=app.pentastic) - Free and [open source](https://github.com/tanujnotes/Pentastic). A minimal tasks, notes and reminders app.

- [Supernova](https://github.com/tanujnotes/Supernova) - Free and [open source](https://github.com/tanujnotes/Supernova) alternative of Nova Launcher.

##

Thank you!

##

### tasks.json 수정 규칙

이 포크의 런처는 기본 브랜치(`master`)의 `data/tasks.json` 을 할 일 목록으로 읽고, 런처에서 바꾼 내용도 같은 파일에 커밋한다. 파일을 고치면 APK 재설치 없이 다음 동기화(앱 시작, 홈 복귀 15분 간격, 6시간마다, 설정의 "Jetzt synchronisieren")에 반영된다. `data/` 만 바뀐 커밋은 APK 빌드를 돌리지 않는다.

스키마

```json
{ "version": 1, "updatedAt": "2026-11-16T09:00:00.000Z",
  "tasks": [
    { "id": "2026-11-16-hwabeop-1", "date": "2026-11-16", "time": null, "title": "화법 1h", "note": "학원 과제", "tag": "3주전", "done": false, "deleted": false, "updatedAt": "2026-11-16T09:00:00.000Z" }
  ] }
```

| 필드 | 값 |
| --- | --- |
| `id` | 고정 문자열. 런처에서 만든 할 일은 `app-` + UUID |
| `date` | `YYYY-MM-DD` |
| `time` | `HH:mm` 또는 `null` (알림은 이 시각, `null` 이면 런처의 기본 알림 시각) |
| `title` `note` `tag` | 문자열, `note` `tag` 는 `null` 가능 |
| `done` `deleted` | `true` / `false` |
| `updatedAt` | 그 할 일이 마지막으로 바뀐 시각, ISO 8601 (`Z` 또는 `+09:00` 같은 오프셋 포함) |

규칙

1. `id` 는 절대 바꾸지 않는다. 바꾸면 다른 할 일로 취급되어 중복된다
2. 할 일을 바꾸면 그 할 일의 `updatedAt` 을 현재 시각으로 갱신한다. 병합은 할 일마다 `updatedAt` 이 더 최신인 쪽을 채택한다 (같으면 파일 쪽)
3. 삭제는 항목을 지우지 않고 `"deleted": true` 와 새 `updatedAt` 으로 표시한다. 항목을 지우면 런처에 남은 사본이 다시 올라온다. `deleted` 상태로 30일 지난 항목은 런처가 동기화할 때 파일에서 지운다
4. 새 할 일은 겹치지 않는 새 `id` 로 추가한다
5. 수정은 런처가 읽는 기본 브랜치(`master`)에 커밋한다. 다른 브랜치나 PR 에만 있으면 런처에 보이지 않는다
6. 파일 맨 위 `updatedAt` 은 참고용이라 병합에 쓰이지 않는다

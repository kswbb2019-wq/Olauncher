# 테마 JSON

설정 → **Theme-JSON** 에 JSON을 붙여넣고 **Anwenden** 을 누르면 빌드 없이 홈 화면과 앱 서랍 외형이 바뀐다.

- 모든 키는 선택이다. 없는 키는 원래 모습(또는 설정 화면 값)을 그대로 쓴다
- JSON에 적은 값이 설정 화면의 정렬 · 날짜/시간 표시보다 우선한다
- 오류가 있으면 아무것도 바뀌지 않고, 몇 번째 줄 · 어떤 키가 문제인지 토스트로 알려준다
- **Kopieren** 은 지금 적용된 JSON을 복사하고, **Zurücksetzen** 은 원래 모습으로 되돌린다
- 글자 크기(sp)는 설정의 Textgröße 배율이 함께 곱해진다

## 일반 Claude 채팅에서 바꾸는 법

아래처럼 이 문서와 현재 JSON(Kopieren 으로 복사)을 함께 붙여넣고 원하는 것을 말한다.

```text
아래 THEME.md 규칙에 맞는 런처 테마 JSON만 답해줘. 설명 없이 JSON 하나만.
[현재 JSON]
{ ... }
[원하는 것]
시계를 왼쪽 위로 더 크게, 앱 이름은 세리프 글꼴로
[THEME.md]
(이 문서 전체)
```

## 전체 예시

```json
{
  "clockVisible": true,
  "dateVisible": true,
  "clockSize": 64,
  "dateSize": 16,
  "clockFont": "sans-serif-light",
  "clockAlign": "left",
  "clockMarginTop": 48,
  "clockMarginSide": 64,
  "homeTextSize": 30,
  "homeFont": "sans-serif-light",
  "homeAlign": "left",
  "homePosition": "center",
  "homeAppSpacing": 10,
  "homeMarginSide": 64,
  "drawerTextSize": 28,
  "drawerFont": "sans-serif-light",
  "drawerAlign": "left",
  "drawerAppSpacing": 10,
  "textColor": "#FFFFFF",
  "shadowColor": "#80000000",
  "shadowRadius": 4,
  "shadowDy": 2
}
```

## 키 목록

### 시계 · 날짜

| 키 | 값 | 설명 |
| --- | --- | --- |
| `clockVisible` | `true` / `false` | 시계 표시 |
| `dateVisible` | `true` / `false` | 날짜 표시 |
| `clockSize` | 숫자 8–200 (sp) | 시계 글자 크기 |
| `dateSize` | 숫자 6–80 (sp) | 날짜 글자 크기 |
| `clockFont` | 글꼴 이름 | 시계 글꼴 |
| `clockAlign` | `left` / `center` / `right` | 시계·날짜 위치. 없으면 `homeAlign` 을 따른다 |
| `clockMarginTop` | 숫자 0–400 (dp) | 화면 위에서 시계까지 거리 |
| `clockMarginSide` | 숫자 0–400 (dp) | 화면 좌우 끝에서 시계까지 거리 |

### 홈 화면 앱

폴더 화면의 이름과 앱 목록에도 같은 키가 적용된다

| 키 | 값 | 설명 |
| --- | --- | --- |
| `homeTextSize` | 숫자 8–120 (sp) | 앱 이름 크기 |
| `homeFont` | 글꼴 이름 | 앱 이름 글꼴 |
| `homeAlign` | `left` / `center` / `right` | 앱 목록 가로 정렬 |
| `homePosition` | `top` / `center` / `bottom` | 앱 목록 세로 위치 |
| `homeAppSpacing` | 숫자 0–100 (dp) | 앱 하나의 위아래 여백 (앱 사이 간격 = 이 값 × 2) |
| `homeMarginSide` | 숫자 0–400 (dp) | 앱 목록 좌우 여백 |

### 앱 서랍

| 키 | 값 | 설명 |
| --- | --- | --- |
| `drawerTextSize` | 숫자 8–120 (sp) | 앱 이름 크기 |
| `drawerFont` | 글꼴 이름 | 앱 이름 글꼴 |
| `drawerAlign` | `left` / `center` / `right` | 앱 이름 정렬 (검색창 포함) |
| `drawerAppSpacing` | 숫자 0–100 (dp) | 앱 하나의 위아래 여백 |

### 공통 글자색 · 그림자 (시계 · 날짜 · 홈 앱 · 서랍 앱)

| 키 | 값 | 설명 |
| --- | --- | --- |
| `textColor` | `#RRGGBB` / `#AARRGGBB` | 글자색. 없으면 다크/라이트 테마 색 |
| `shadowColor` | `#RRGGBB` / `#AARRGGBB` | 그림자 색 |
| `shadowRadius` | 숫자 0–25 (px) | 그림자 번짐. `0` 이면 그림자 없음 (기본 4) |
| `shadowDy` | 숫자 -20–20 (px) | 그림자 세로 위치 (기본 2) |

### 글꼴 이름

기기에 들어 있는 시스템 글꼴만 쓴다 (APK에 글꼴 파일 없음).

`sans-serif` `sans-serif-light` `sans-serif-thin` `sans-serif-medium` `sans-serif-black` `sans-serif-condensed` `sans-serif-condensed-light` `serif` `monospace` `serif-monospace` `casual` `cursive` `sans-serif-smallcaps`

기기에 없는 글꼴은 기본 글꼴로 보인다. 새 글꼴 파일을 넣거나 새 키를 만드는 것은 Claude Code 작업(빌드 필요)이다.

## 규칙

- JSON 객체 하나 `{ ... }` 만 허용, 끝 쉼표 불가
- 숫자는 따옴표 없이 (`"clockSize": 64`), 나머지 값은 따옴표 안에
- 목록에 없는 키는 오류 (오타 방지)

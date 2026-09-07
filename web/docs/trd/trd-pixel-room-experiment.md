---
document_type: trd
status: experiment
scope: web
last_updated: 2026-09-07
---

# [TRD] 도트 캐릭터 걷기·모자 레이어 실험

## 목적과 경계

캐릭터 꾸미기 제품 범위를 구현하기 전에, 한 캐릭터의 짧은 좌우 걷기와 모자 레이어 정렬이 현재 React 웹에서 자연스럽게 동작하는지 확인한다. 개발 모드의 `/experiments/pixel-room`에서 로그인 없이 접근하며 프로덕션 라우트에는 포함하지 않는다.

이 실험은 실제 홈·마이페이지, 상점, 코인, 보상, 구매, 로그인, API와 영속 저장을 변경하지 않는다. 모자 선택은 페이지를 닫으면 사라지는 React 로컬 상태다.

## 자산과 합성

- `room.png`: 빈 공부방 배경이다.
- `character-walk.png`: 2×2 배열의 4프레임 정사각형 셀 스프라이트다.
- `hats.png`: 같은 캐릭터 캔버스에 정렬된 1×2 모자 셀 스프라이트다.
- 생성 원본이 실제 alpha 대신 밝은 체크 무늬를 포함하므로 원본 파일을 직접 변형하지 않고, `PixelSprite`가 실험용 SVG white-matte 표시 필터와 crop으로 프레임·모자 레이어를 합성한다. 이 필터는 밝은색 의상·아이템까지 지울 수 있어 정식 자산에는 사용할 수 없다. 제품 구현 전에는 검수된 투명 PNG로 교체해야 한다.
- 이동·방향·크기는 `PixelSprite` 바깥 장면이 맡아 자산 표시 보정과 움직임 계산을 분리한다.
- 모든 도트 자산은 `image-rendering: pixelated`로 표시한다.
- 상체와 머리는 첫 프레임으로 고정하고 490px 아래 발 영역만 4프레임으로 순환한다. 프레임별 위치 차이를 보정해 고정 모자 아래에서 얼굴이 흔들리는 것을 줄였다. 좌우 이동은 정면을 유지하는 짧은 셔플이며 옆모습 보행이나 별도 상의 교체는 검증하지 않았다.
- 생성 도구·크기·프롬프트는 [자산 기록](../../src/experiments/pixel-room/assets/README.md)에 보관한다.

## 움직임 상태

정규화 위치 `0..1`, 방향 `-1 | 1`, 실행 상태와 누적 보행 시간을 상태로 둔다. 프레임 시간으로 이동 거리를 계산하고 경계를 넘은 잔여 거리만큼 반대 방향으로 되돌려 낮은 프레임에서도 벽 밖으로 나가지 않는다. 브라우저 탭 복귀 시 큰 시간차로 캐릭터가 튀지 않도록 화면의 프레임 입력은 64ms로 제한한다.

사용자는 왼쪽·오른쪽 보기, 일시정지·재개와 모자 세 가지를 터치 또는 키보드로 선택한다. `prefers-reduced-motion: reduce`에서는 자동 이동과 걷기 프레임을 정지하고 방향·모자 미리보기만 유지한다.

## 검증

- 모션 모델 단위 테스트: 경계 반전, 긴 프레임 지연, 일시정지·재개, 모션 감소, 프레임 순환
- 320px 이상 모바일 폭과 640px 최대 본문 폭에서 가로 넘침 확인
- 터치·키보드 버튼 조작과 선택 상태의 텍스트·`aria-pressed` 확인
- `pnpm verify`로 라이선스, 타입 검사, lint와 production build 확인

### 2026-09-07 실험 결과

- PASS: `node --test src/experiments/pixel-room/pixelRoomMotion.test.mjs` 5개, `pnpm typecheck`, `pnpm lint`, `pnpm build`.
- PASS: 브라우저에서 걷기 프레임·위치 변화, 정지·재개, 모자 3개 상태, 좌우 반전, 키보드 Enter 선택 확인. 320·390·768·1440px에서 가로 넘침 없이 넓은 화면 본문 640px 확인. 조작은 SEED large 버튼으로 최소 44px 이상 확보.
- PASS: production build 산출물에 실험 route·캐릭터/모자/방 자산이 포함되지 않음.
- PRE-EXISTING FAILURE: `pnpm verify`의 `licenses:check`가 `Third-party notices are out of date`로 중단. 이 브랜치는 dev의 package/lock/고지 파일을 변경하지 않았다. 나머지 검사는 개별 실행했다.
- BLOCKED: 실제 iOS/Android WebView와 기기의 모션 감소 설정은 이 환경에서 미검증. 모션 감소 분기는 단위 테스트로만 검증했다.

## 실행

`web/`에서 `pnpm dev --host 127.0.0.1 --port 5178 --strictPort`를 실행하고 `http://127.0.0.1:5178/experiments/pixel-room`을 연다. 로그인과 서버 API 없이 실험할 수 있다. 원본 dev 기능은 기존 경로에서 유지한다.

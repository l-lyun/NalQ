---
document_type: execution-plan
status: draft
scope: release-readiness
last_updated: 2026-09-02
---

# [Execution Plan] OpenMD 릴리스 준비

- 관련 제품 기반: [OpenMD 제품 기반](../product.md)
- 관련 PRD: [학습자료 만들기](../prd/prd-content-import.md), [퀴즈 생성·풀이·결과·복습](../prd/prd-quiz-learning.md), [마이페이지와 계정 관리](../prd/prd-mypage-account-management.md)
- 관련 UX: [학습자료 만들기 흐름](../ux/flow-content-import.md), [퀴즈 생성부터 복습까지](../ux/flow-quiz-solving.md)
- 관련 Contract: [학습자료·퀴즈·복습 API](../contracts/contract-api-quiz-learning.md), [인증 API](../contracts/contract-api-authentication.md), [사용자·인증 데이터](../contracts/contract-data-authentication.md)
- 관련 TRD: [Notion 가져오기 서버](../../server/docs/trd/trd-notion-page-import.md), [퀴즈·복습 저장 모델](../../server/docs/trd/trd-quiz-grading.md), [웹 퀴즈 상태](../../web/docs/trd/trd-quiz-solving.md), [Expo WebView 앱 셸](../../app/docs/trd/trd-webview-shell.md)
- 관련 검증 계획: [정적 검증 하네스](plan-static-verification-harness.md)

## 문서 책임

이 문서는 2026-09-02 현재 열린 PR과 구현 상태를 기준으로, OpenMD를 웹 제한 베타부터 공개 웹 및 네이티브 스토어까지 올리기 전에 필요한 작업 순서와 단계별 Go 기준을 관리한다. 기능의 사용자 정책, 공유 API 의미와 애플리케이션 내부 구현을 새로 확정하지 않으며, 결정이 필요한 항목은 해당 PRD·Contract·TRD에서 승인받은 뒤 이 계획의 작업으로 연결한다.

## 목표와 완료 조건

### 목표 — 제안

- 열린 PR을 의존 순서대로 안전하게 병합하고, 현재 리뷰에서 확인된 사용자 흐름·자격 관리 위험을 해소한다.
- 임시 퀴즈 생성 worker를 실제 LLM 생성 경계로 교체하되 기존 퀴즈 검증·상태·오류 계약을 보존한다.
- 목표 배포 단계별로 기능, 운영, 개인정보와 복구 조건을 관찰 가능한 Go 기준으로 둔다.
- CI 성공과 실제 배포 가능성을 구분하고, 운영 환경에서만 확인 가능한 항목을 별도 체크한다.

### 완료 조건 — 제안

- 선택한 배포 단계의 Go 기준이 모두 충족되고, 미충족 항목은 배포 승인자가 명시적으로 No-Go로 분류한다.
- 병합 대상 최신 commit에 P1·P2 수정 필요 리뷰가 남아 있지 않고, 최신 commit 기준 재검토와 전체 검증이 완료된다.
- 실제 LLM 성공뿐 아니라 timeout, rate limit, 잘못된 구조화 응답, 유효 문제 0건과 서버 재시작 시나리오가 사용자를 무기한 `GENERATING`에 남기지 않는다.
- 운영 구성, migration, backup·restore, rollback과 관측 경로가 배포 전에 같은 후보 artifact로 검증된다.

## 범위와 비범위

### 범위

- PR #41, #42, #43의 병합 준비와 stacked PR 순서
- 실제 LLM 문제 생성, Notion·웹 이탈 보호와 비동기 복구
- 웹 제한 베타, 공개 웹, 네이티브 스토어의 단계별 릴리스 게이트
- 운영 환경변수·비밀, CORS·Cookie, DB migration, backup·rollback과 observability
- 이용약관·개인정보 처리방침·회원 탈퇴 준비
- 네이티브 OAuth 복귀와 Cookie 실기기 검증

### 비범위

- 이 계획에서 특정 LLM 제공자·모델·가격제·무료 사용량을 확정하는 것
- 운영 웹/API topology, 데이터 보존 기간과 탈퇴 처리 기한을 승인 없이 고정하는 것
- 실제 인프라 생성, 비밀 주입, 배포 실행과 스토어 제출
- 경험치·랭킹·친구·상점 등 초기 범위 밖 기능

## 현재 기준선 — 확인된 사실

### 열린 PR과 검증 범위

| PR | 기준 관계 | 2026-09-02 확인 상태 | 릴리스 전 의미 |
| --- | --- | --- | --- |
| [#41 Notion 페이지 가져오기 서버](https://github.com/l-lyun/openmd/pull/41) | `dev` ← `codex/notion-social-login-server` | GitHub `web-static`, `server-fast`, `server-integration`, `harness-required` 성공. 자동 리뷰는 `a29c284`를 검토했다. | CI 성공과 별개로 아래 Notion P2를 최신 commit에서 재현·수정·재검토해야 한다. |
| [#42 Notion 학습자료 가져오기 웹](https://github.com/l-lyun/openmd/pull/42) | #41 브랜치 ← `codex/notion-import-ui-design` | #41 위에 쌓인 stacked PR이며 현재 GitHub 검사 성공. 자동 리뷰는 `41f48b9`를 검토했고 이후 commit이 있다. | #41보다 먼저 병합하지 않는다. 최신 head 전체가 재검토되지 않았으므로 아래 P1·P2를 최신 commit 기준으로 다시 확인한다. |
| [#43 WebView 셸 정책 테스트](https://github.com/l-lyun/openmd/pull/43) | `dev` ← `codex/app-shell-policy-tests` | GitHub 검사 성공, 현재 검토에서 변경 범위의 수정 필요 문제는 확인되지 않았다. | 독립 병합 가능하지만 현재 required CI는 앱의 `pnpm test`·`pnpm typecheck`를 실행하지 않는다. 병합 전 별도 실행 결과가 필요하다. |

이 계획 브랜치의 base인 `dev`에는 #41·#42 구현이 아직 들어오지 않았다. 따라서 아래 Notion 구현 항목은 현재 `dev`의 기능으로 표현하지 않고, 해당 PR의 병합 전 조건으로 관리한다.

### 현재 퀴즈 생성 경계

- 생성 접수 API는 `selectedTypes`, `difficulty`, `maxQuestionCount`를 검증하고 응답의 `requestedConfig`에 돌려준다.
- 현재 내부 `TemporaryQuizGenerationRequested`에는 사용자, QuizSet, 문제 유형과 최대 개수만 전달되어 난이도와 학습자료 본문이 실제 생성 worker 입력 경계에 포함되지 않는다.
- 현재 worker는 단일 서버 메모리 scheduler와 임시 문제 생성기를 사용한다. 서버가 재시작되면 진행 중 생성을 복구하지 않고 `FAILED`로 정리한다.
- 공유 계약은 유효 후보를 유형별로 검증한 뒤 하나 이상이면 `READY`, 하나도 없으면 `FAILED`로 끝내고, 생성 제공자의 원시 payload·프롬프트·내부 오류를 공개 응답에 포함하지 않도록 이미 정의한다.

## 열린 PR 병합 게이트와 순서 — 제안

### PR #41: Notion 서버

자동 리뷰에서 확인된 다음 여섯 P2는 thread의 해결 표시만 보지 않고 최신 코드와 회귀 테스트로 다시 확인한다.

1. 다른 워크스페이스 callback에서 새 자격 revoke가 불명확하게 실패했을 때 자격을 버리지 않고 철회 확인 또는 후속 정리가 가능해야 한다.
2. 이스케이프된 대체 텍스트·괄호가 있는 Markdown 이미지에서도 미디어 URL을 제거하고 허용된 캡션만 남겨야 한다.
3. fenced code의 닫는 구분자는 뒤에 공백 외 문자가 없는 실제 닫는 줄만 인정해야 한다.
4. revoke 뒤 introspection은 `active`가 명시적 boolean `false`일 때만 비활성으로 신뢰해야 한다.
5. OAuth state key 저장과 사용자별 인덱싱을 원자화해 연결 해제와 승인 시작의 경합으로 늦은 callback이 연결을 되살리지 못하게 해야 한다.
6. callback 결과 query를 URI fragment 앞에 구성하거나 fragment가 있는 복귀 URI를 거부해야 한다.

완료 조건은 각 시나리오의 실패 테스트를 먼저 확인하고, 서버 집중 테스트와 `./scripts/verify.sh all`이 최신 head에서 통과하며, 최신 commit 재검토에서 같은 문제가 재현되지 않는 것이다.

### PR #42: Notion 웹과 학습자료 편집

#### P1 — 병합 차단

- 브라우저 history back과 Android 시스템 back도 화면 상단 뒤로가기와 같은 미저장 초안 이탈 확인을 거쳐야 한다. 확인 없이 제목·본문이 사라져서는 안 된다.
- 운영에서 `VITE_QUIZ_API_ENABLED`가 비활성인데 `저장하고 퀴즈 만들기`가 미등록 퀴즈 route로 이동해 홈으로 튕기는 흐름을 제거해야 한다. 운영 배포에서는 기능과 route가 같은 runtime mode를 사용하고, 비활성 상태의 사용자 행동을 명시해야 한다.

#### P2 — 수정 필요

- `내 퀴즈 → 퀴즈 만들기`에서 전달된 목록의 검색어·페이지·펼침·스크롤 복귀 맥락이 기존 학습자료 선택, 새 자료 입력, Notion, 조건 설정 하위 흐름에서 끊기지 않아야 한다.
- 검색 변경 시 이전 페이지네이션 응답과 cursor를 폐기하고, 검색 입력이 시작되면 이전 페이지 선택으로 가져오기를 실행하지 못하게 해야 한다.
- 가져오기 요청 중 이탈했을 때 늦은 성공 응답이 사용자를 편집 화면으로 다시 이동시키지 않아야 한다.
- 페이지 목록의 `NOTION_REAUTH_REQUIRED`는 연결 상태를 다시 조회해 재인증 행동으로 복구하고, 새로고침 실패의 재시도는 실패한 첫 배치 요청을 다시 수행해야 한다.
- 잘못 percent-encoding된 Notion URL은 화면 예외가 아니라 유효성 오류로 처리해야 한다.
- 연결 관리 Bottom Sheet에서 OAuth 시작 실패와 재시도 행동이 현재 열린 맥락 안에 보여야 한다.

완료 조건은 상단 back·브라우저 back·Android WebView back, 느린 응답 순서 역전, 이탈 뒤 늦은 응답, 기능 flag 비활성 production build를 자동 행동 테스트와 실제 브라우저에서 확인하고 최신 head를 다시 리뷰하는 것이다.

### PR #43: 앱 셸 정책 테스트

- #41·#42와 코드 의존성이 없으므로 먼저 병합하거나 두 PR의 수정과 병렬 검토할 수 있다.
- 병합 전 `app/`에서 `pnpm test`와 `pnpm typecheck`를 별도로 실행한다.
- #43 병합은 Notion OAuth deep link, Cookie 실기기 검증 또는 앱스토어 준비 완료를 뜻하지 않는다.

### 권장 병합 순서

1. 독립 PR #43을 앱 검증 후 병합한다.
2. PR #41의 여섯 P2를 수정·재검토한 뒤 `dev`에 병합한다.
3. PR #42의 base를 갱신된 `dev`로 정리하고 stacked diff에 #41 변경이 중복되지 않는지 확인한다.
4. PR #42의 P1·P2 수정과 최신 head 재검토·전체 검증 후 병합한다.
5. 세 PR이 합쳐진 `dev`의 동일 commit에서 웹 로그인 → Notion 연결 → 가져오기 → 편집 → 저장 → 퀴즈 생성 진입 smoke test를 다시 실행한다.

## 실제 LLM 문제 생성 범위 — 제안

실제 LLM 연결은 응답 문자열을 교체하는 작업이 아니라, 기존 OpenMD 문제 계약으로 변환하고 작업을 반드시 terminal state로 끝내는 서버 경계다. 외부 제공자의 원시 형식은 [문서 운영 가이드의 Contract 원칙](../guide.md#contract-작성-원칙)에 따라 웹·앱에 노출하지 않는다.

### 입력과 snapshot

- 생성기는 잠긴 학습자료의 현재 본문과 사용자가 요청한 `difficulty`, `selectedTypes`, `maxQuestionCount`를 모두 받아야 한다.
- 어떤 시점의 본문이 근거인지와 worker가 본문을 읽는 방식은 서버 TRD에서 정하되, 생성 중 본문 잠금과 기존 QuizSet 불변 정책을 바꾸지 않는다.
- 사용자 식별자·이메일·Notion 토큰·원시 Notion URL은 프롬프트 입력에서 제외한다.

### 구조화 응답과 검증

- 제공자 응답은 provider-neutral 후보 모델로 변환하되, 유형별 공개 필드와 `READY`·`FAILED` 의미는 [학습·퀴즈 API Contract의 상태·풀이 데이터 조회](../contracts/contract-api-quiz-learning.md#상태풀이-데이터-조회)를 그대로 따른다.
- 후보 검증, 제외, 순서 재부여와 저장 상태 전이는 [서버 퀴즈 채점 TRD의 생성 후보 확정](../../server/docs/trd/trd-quiz-grading.md#36-생성-후보-확정)을 구현 기준으로 삼고 이 실행 계획에서 다시 정의하지 않는다.
- schema 위반 원시 응답, 프롬프트와 검증 상세를 클라이언트 오류로 전달하지 않는다.

### timeout, retry와 실패 확정

- 연결·응답·전체 작업 deadline과 retry 가능한 제공자 오류를 서버 TRD에서 수치로 확정한다.
- timeout, rate limit, 일시 5xx는 제한된 횟수만 재시도하고, 인증·quota 소진·구조화 응답 반복 실패는 무한 재시도하지 않는다.
- 모든 실패 경로는 QuizSet을 `FAILED`로 확정하고 학습자료 본문 잠금을 해제한다. 실패 확정 자체가 실패하면 별도 경보와 복구 작업 대상이 되어야 한다.
- 생성 접수 응답을 잃은 클라이언트는 현재 Contract대로 활성 생성을 먼저 조회하며, 같은 요청을 즉시 중복 제출하지 않는다.
- 서버 재시작과 다중 instance에서 작업을 유실하지 않을 작업 원장·lease·queue를 공개 웹 전에 결정한다. 제한 베타에서 현재 fail-on-restart를 임시 허용하려면 무기한 `GENERATING`이 없고 사용자가 재시도할 수 있다는 검증과 운영 승인 기록이 필요하다.

### 민감 정보와 로그

- 학습자료 본문, 문제·정답 원문, 전체 prompt, provider 원시 request·response, API key와 Notion 자격을 일반 로그·분석 사건·오류 추적 payload에 넣지 않는다.
- 허용 로그는 request/trace ID, 사용자 내부 식별자, QuizSet ID, 제공자 작업 분류, 지연 시간, token 사용량·비용 집계, 공개 실패 분류처럼 복구와 비용 관리에 필요한 최소 메타데이터로 제한한다.
- provider가 입력을 보관하거나 학습에 사용하는지, 보관 기간과 지역은 개인정보 처리방침 승인 전에 확인한다.

### quota와 비용 보호

- 사용자·시간 구간별 생성 허용량, 동시 생성 상한, 월간 비용 한도와 한도 초과 사용자 안내를 실제 호출 전에 적용한다.
- timeout·retry·부분 성공이 사용량과 사용자 quota에 어떻게 반영되는지는 제품 정책과 서버 계약에서 확정한다.
- token·비용 metric과 예산 임계 경보를 두고, 예산 초과 시 새 생성만 안전하게 중단하되 기존 풀이·결과 조회는 유지한다.

## 배포 단계별 Go 기준 — 제안

### 1단계: 웹 제한 베타

초대된 소수 사용자와 되돌릴 수 있는 운영 범위를 전제로 한다.

- PR #41·#42·#43의 해당 병합 게이트를 충족한다.
- 실제 LLM으로 `붙여넣기 또는 Notion 가져오기 → 편집·저장 → 조건 선택 → 생성 → 풀이 → 결과`가 완료된다.
- timeout, rate limit, 구조화 응답 실패, 서버 재시작에서 무기한 생성·본문 잠금이 없고 실패 후 재시도할 수 있다.
- 운영 비밀은 저장소·프론트 bundle·로그에 없고, 개발 기본값이 운영에 사용되지 않는다.
- 웹/API의 실제 origin에 맞는 CORS·Cookie·CSRF 설정과 HTTPS를 확인한다.
- production-like DB에서 migration과 이전 버전 code compatibility를 확인하고, 배포 전 backup과 restore 절차를 한 번 검증한다.
- 오류율·생성 성공률·생성 지연·provider 비용·stuck generation·DB/Redis 상태를 볼 dashboard와 최소 경보가 있다.
- 실제 사용자의 학습자료가 외부 LLM과 Notion을 거친다는 개인정보 안내, 적용 약관과 데이터 삭제 요청 채널을 제공한다. 셀프서비스 탈퇴를 제한 베타에 포함할지는 별도 승인한다.
- 알려진 제한, 사용자 초대·중단 방법과 rollback 담당자가 기록되어 있다.

### 2단계: 공개 웹

웹 제한 베타 기준에 더해 다음을 충족한다.

- 사용자 quota·비용 정책과 초과 안내가 승인·적용되고, 악의적·반복 생성이 예산을 우회하지 못한다.
- durable generation 또는 동등한 재처리 전략이 다중 instance·재시작에서도 중복·유실 없이 동작한다.
- backup restore와 이전 artifact rollback을 production-like 환경에서 rehearsal하고 목표 복구 시간·데이터 손실 범위를 승인한다.
- 서비스 이용약관과 개인정보 처리방침의 운영 전문·버전·적용일이 승인되고 사용자가 마이페이지에서 확인할 수 있다.
- 회원 탈퇴 재인증, 모든 세션 폐기, 학습자료·문제·풀이·Notion 자격의 삭제 또는 비식별화 정책과 운영 실패 복구가 구현된다.
- 가용성·지연·생성 실패·비용에 대한 운영 목표와 alert owner, 장애 공지·문의 채널을 정한다.
- 브라우저 행동 테스트와 API 계약 검사가 CI 필수 게이트에 포함되거나, 동일 위험을 막는 승인된 대체 검증이 있다.

### 3단계: iOS·Android 스토어

공개 웹 기준에 더해 다음을 충족한다.

- 외부 브라우저로 연 Notion OAuth가 App/Universal Link 또는 승인된 deep link를 통해 원래 WebView와 의도한 route로 복귀한다.
- iOS·Android 실기기에서 로그인, 앱 종료·재실행, refresh 회전, logout의 HttpOnly Cookie 유지·갱신·삭제를 검증한다.
- Android 시스템 back과 iOS gesture가 미저장 편집·풀이 이탈 확인을 한 번만 실행하고 history를 예측 가능하게 이동한다.
- 실제 기기에서 키보드, safe area, 작은 viewport, 큰 글자, 외부 링크, 네트워크 중단과 WebView renderer 복구를 확인한다.
- `app` test·typecheck와 배포 대상 development/preview build 검증이 CI 또는 릴리스 체크에 포함된다.
- 앱 개인정보 표시, 계정 삭제 진입, 지원 URL, 스토어 데이터 수집 고지, 서명·bundle/package 식별자와 플랫폼별 심사 항목을 완료한다.

## 운영 준비 체크리스트 — 제안

### 환경변수와 비밀

- 서버: DB·Redis 연결, Access Token·이메일 코드 비밀, 메일, Notion OAuth client 자격·callback·허용 return URI·토큰 암호화 key ring, LLM API 자격·provider 설정을 환경별 inventory로 관리한다.
- 웹: `VITE_API_BASE_URL`, `VITE_QUIZ_API_ENABLED`와 기타 build-time feature flag의 운영 값을 후보 artifact 생성 시 고정하고 기록한다. 비활성 route와 노출 CTA가 어긋나지 않게 한다.
- 앱: `EXPO_PUBLIC_WEB_URL`에는 공개 HTTPS 주소만 넣고 비밀을 넣지 않는다.
- 비밀은 저장소, build artifact, CI log, exception과 client 응답에 없어야 한다. 소유자·회전 절차·폐기 절차와 이전 암호화 key 복호화 기간을 runbook으로 둔다.

### CORS, Cookie와 배포 topology

- 웹/API same-origin reverse proxy 또는 same-site 별도 host 중 실제 topology를 먼저 결정한다.
- `OPENMD_CORS_ALLOWED_ORIGINS`와 브라우저 인증 origin은 정확한 HTTPS origin만 허용하고 wildcard credentialed CORS를 사용하지 않는다.
- topology에 맞춰 Cookie의 `Secure`, `SameSite`, host-only 이름과 path, CSRF header·Origin 검증을 확인한다.
- SPA 직접 URL과 새로고침이 `index.html`로 복구되고 API 경로와 충돌하지 않는지 확인한다.

### DB migration, backup과 rollback

- 모든 Flyway migration을 production-like MySQL의 현재 버전과 데이터가 있는 snapshot에 적용한다.
- code와 schema의 배포 순서를 이전 버전과 호환되게 설계하고, 하향 migration을 즉석에서 실행하는 방식에 의존하지 않는다.
- 배포 직전 backup, restore rehearsal, 복구 확인 query와 담당자를 기록한다.
- rollback은 이전 검증 artifact와 호환 schema로 code를 되돌리는 절차, feature flag로 새 생성만 중지하는 절차, 비가역 데이터 변경의 forward-fix 절차를 구분한다.

### Observability와 운영 복구

- 서비스 공통: 배포 version, HTTP 오류율·지연, DB pool, Redis, 인증 refresh 오류를 관측한다.
- Notion: OAuth callback 결과 분류, 연결·가져오기 지연, provider rate limit·timeout·재인증·철회 미확인을 본문·token 없이 집계한다.
- LLM: 접수·`READY`·`FAILED`, 생성 소요 시간, 유효 후보 수, retry, token·비용, stuck `GENERATING`을 집계한다.
- 각 경보에 확인 dashboard, 담당자, 완화 행동, 사용자 공지 기준과 사후 복구 query를 연결한다.

## 작업 순서 — 제안

| ID | 우선순위 | 작업 | 완료 조건 |
| --- | --- | --- | --- |
| T001 | P0 | #41 Notion 서버 리뷰 수정 | 여섯 P2의 회귀 테스트, 최신 head 재검토, 서버 전체 검증 완료 |
| T002 | P0 | #42 웹 P1·P2 수정 | 미저장 back, 비활성 route, return 맥락과 비동기 경합을 행동 테스트·브라우저에서 확인 |
| T003 | P0 | #43 독립 병합 검증 | `app` test·typecheck PASS 후 병합, 앱 CI 공백은 후속 작업으로 등록 |
| T004 | P0 | LLM 제품 정책·공유 계약·서버 TRD 승인 | provider 중립 입력·출력·실패, quota와 민감정보 정책의 확정·제안·열린 질문 분리 |
| T005 | P0 | 실제 LLM adapter와 생성 worker 구현 | 본문·난이도·유형·개수 전달, 구조 검증, terminal state·잠금 해제, 실패 테스트와 통합 테스트 PASS |
| T006 | P0 | 제한 베타 E2E와 운영 구성 | 실제 운영 후보 환경의 핵심 여정, CORS·Cookie·secret·migration·backup·관측 확인 |
| T007 | P1 | quota·비용·durable job 경계 | 공개 웹의 중복·유실·예산 초과 시나리오와 사용자 오류 행동 검증 |
| T008 | P1 | 약관·개인정보·탈퇴 | 승인 전문·버전 노출, 재인증·세션 폐기·데이터 처리·운영 복구 검증 |
| T009 | P1 | 공개 웹 release rehearsal | 동일 artifact 전체 검사, restore·rollback·장애 대응 rehearsal와 배포 승인 기록 |
| T010 | P2 | 네이티브 OAuth·Cookie·스토어 준비 | iOS·Android 실기기 시나리오, app 검증 게이트와 스토어 필수 항목 완료 |

P0·P1·P2는 GitHub 리뷰 severity가 아니라 이 실행 계획 안의 권장 순서다. 목표가 웹 제한 베타이면 T001~T006, 공개 웹이면 T001~T009, 네이티브 스토어이면 T001~T010이 대상이다.

## 검증 계획

### 자동 검증

| 범위 | 명령 또는 게이트 | 확인 대상 |
| --- | --- | --- |
| 저장소 빠른 검사 | 루트 `./scripts/verify.sh fast` | 웹 정적 검증과 Docker 없는 서버 회귀 |
| 배포 전 전체 검사 | 루트 `./scripts/verify.sh all` | 웹 정적 검증, 서버 fast·Testcontainers 통합 테스트 |
| 웹 | `web/`의 `pnpm verify`와 승인된 행동 테스트 명령 | typecheck·lint·build, back·flag·비동기 사용자 흐름 |
| 서버 | `server/`의 `./gradlew fastTest`, `./gradlew integrationTest`, 필요 시 `./gradlew test` | LLM·Notion 단위, 계약, MySQL·Redis 통합 |
| 앱 | `app/`의 `pnpm test`, `pnpm typecheck` | URL 정책, WebView 셸 상태와 타입 |
| diff | `git diff --check` | 공백·patch 오류 |

실행하지 않은 검사는 PASS로 보고하지 않고 [정적 검증 하네스의 결과 보고 규칙](plan-static-verification-harness.md#결과-보고-규칙)에 따라 `PASS`, `BLOCKED`, `PRE-EXISTING FAILURE`로 기록한다.

### 사용자 가시·운영 검증

- 웹 핵심 여정을 운영과 같은 HTTPS origin, production build와 실제 서버로 확인한다.
- LLM provider는 정상 응답 외에 느린 응답, 429, 5xx, 잘못된 JSON·schema, 유효 후보 0건과 응답 유실을 통제된 fixture 또는 sandbox로 재현한다.
- 서버 종료·재시작, worker 중복 실행과 실패 확정 오류에서 QuizSet·학습자료 잠금·비용 집계가 일관적인지 확인한다.
- migration·restore·rollback은 production-like 데이터 복제본에서 실행하고 파괴적인 rehearsal을 운영 DB에서 직접 수행하지 않는다.
- 네이티브 단계는 시뮬레이터만으로 완료 처리하지 않고 지원 대상 iOS·Android 실기기를 포함한다.

## 진행 기록

| 날짜 | 상태 | 결과 또는 차단 사유 |
| --- | --- | --- |
| 2026-09-02 | 계획 초안 | 열린 PR 3개, 기존 PRD·Contract·TRD와 배포 준비도 검토를 하나의 단계별 실행 계획으로 정리 |

## 열린 질문과 승인 필요 사항

- 첫 배포 목표를 팀 내부 데모, 초대형 웹 제한 베타, 공개 웹 중 어디로 둘 것인가?
- 실제 LLM 제공자·모델, 데이터 보관·학습 제외 조건과 장애 시 대체 제공자를 어떻게 승인할 것인가?
- 무료 생성량, 사용자·시간별 quota, 재시도 과금과 월간 예산 상한은 무엇인가?
- 제한 베타에서 fail-on-restart와 사용자 재시도를 임시 허용할지, 첫 LLM 배포부터 durable job을 요구할 것인가?
- 운영 웹과 API를 same-origin으로 둘지 same-site 별도 host로 둘지?
- 이용약관·개인정보 처리방침의 운영 전문과 버전, LLM·Notion 제3자 처리 고지 범위는 무엇인가?
- 탈퇴 데이터의 삭제·비식별화 범위, 법정 보존 예외, 완료 기한과 동일 이메일 재가입 시점은 무엇인가?
- 네이티브 출시는 iOS·Android 동시인지, 한 플랫폼을 먼저 검증할지?

이 질문들은 답에 따라 제품 정책, 공유 계약 또는 운영 topology가 달라지므로 이 실행 계획에서 임의로 확정하지 않는다.

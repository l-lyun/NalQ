---
document_type: trd
status: implemented
scope: server
---

# [TRD · Server] Notion 단일 페이지 가져오기 서버 설계

- 상태: 구현 동기화
- 대상: Notion Public OAuth 연결, 페이지 선택, Markdown 일회성 복사
- 제품 정책: [학습자료 만들기 PRD](../../../docs/prd/prd-content-import.md)
- 사용자 흐름: [학습자료 만들기 흐름](../../../docs/ux/flow-content-import.md)
- 공유 계약: [학습자료·퀴즈 API 계약](../../../docs/contracts/contract-api-quiz-learning.md)
- 패키지 기준: [서버 패키지 구조](trd-package-structure.md)

## 1. 문서 책임

이 문서는 확정된 Notion 공유 계약을 서버에서 구현하기 위한 MySQL·Redis 상태, 자격 암호화, 외부 API 경계, 동시성, 변환 파이프라인과 기술 검증을 소유한다. 사용자에게 보이는 행동·문구·HTTP 필드와 오류 의미는 PRD·Flow·API Contract가 원장이며 이 문서가 재정의하지 않는다.

## 2. 범위와 비범위

### 범위

- NalQ 사용자별 Notion Public OAuth 연결 하나
- OAuth state의 Redis 15분 TTL·일회 소비와 고정 복귀 대상 검증
- MySQL 연결 원장과 AES-256-GCM 토큰 암호화
- access token 인증 실패 시 refresh token 교체 동시성
- 접근 페이지 검색·cursor 조회, 선택 페이지 제목·Markdown 일회성 복사
- Notion 응답의 NalQ 성공·오류 모델 변환
- 실제 Notion 자격에 의존하지 않는 단위·계약·MySQL·Redis 통합 검증

### 비범위

- Notion 원본과 저장 학습자료의 동기화
- 워크스페이스 다중 연결, 페이지 목록·`pageId`의 DB 저장
- 이미지·동영상·오디오·PDF·첨부파일 다운로드와 객체 저장소 업로드
- 회의록 녹취 요청, `unknown_block_ids` 추가 회수·재조립
- Notion 본문 검색·색인, 복잡한 자체 rate-limit 큐
- 웹 화면 레이아웃·UX 문구와 네이티브 앱 OAuth 복귀

## 3. 모듈과 의존 방향

기존 `com.openmd.server.learningmaterial` 도메인 안에 학습자료 가져오기 진입점을 두고, Notion 연결·자격·외부 호출은 교체 가능한 통합 경계로 분리한다.

- `learningmaterial.controller`: `POST /api/v1/learning-material-imports/notion`의 Bearer 인증, request·response 변환
- `learningmaterial.service`: 선택 페이지 일회성 가져오기 조정과 성공 결과 투영
- `integration.notion.controller`: 연결 상태, OAuth 시작·callback, 페이지 목록, 연결 해제
- `integration.notion.service`: 연결 상태 전이, 워크스페이스 제약, 자격 갱신·폐기 조정
- `integration.notion.repository`: MySQL 연결 원장과 Redis OAuth state
- `integration.notion.client`: Notion REST API 전용 port·adapter. Controller·domain은 Notion 원시 JSON·오류를 알지 못한다.
- `integration.notion.crypto`: 토큰 암·복호화와 키 버전 선택. 평문 토큰을 DTO·entity·로그로 전파하지 않는다.

기본 참조 방향은 `Controller -> Service -> Repository|Client|Crypto`다. Notion adapter는 도메인 공개 오류를 직접 던지지 않고 제공자 중립 결과로 변환한다.

## 4. MySQL 연결 원장

새 Flyway migration은 `notion_connections` 테이블을 만든다.

| 컬럼 | 타입 | 규칙 |
| --- | --- | --- |
| `id` | `BIGINT` | PK, auto increment |
| `user_id` | `BIGINT` | `users.id` FK, not null, unique |
| `workspace_id` | `VARCHAR(36)` | Notion workspace UUID, not null |
| `workspace_name` | `VARCHAR(255)` | Notion이 제공한 표시용 snapshot, nullable |
| `access_token_ciphertext` | `TEXT` | Base64 암호문·tag, not null |
| `access_token_nonce` | `BINARY(12)` | GCM nonce, not null |
| `refresh_token_ciphertext` | `TEXT` | nullable |
| `refresh_token_nonce` | `BINARY(12)` | refresh token이 있을 때만 존재 |
| `pending_revocation_workspace_id` | `VARCHAR(36)` | 다른 워크스페이스 자격 철회 확인 대기 중에만 존재 |
| `pending_revocation_token_ciphertext` | `TEXT` | 철회 확인 대기 access token 암호문, nullable |
| `pending_revocation_token_nonce` | `BINARY(12)` | pending token이 있을 때만 존재 |
| `pending_revocation_key_version` | `VARCHAR(32)` | pending token 복호화 키 선택, nullable |
| `pending_revocation_created_at` | `TIMESTAMP(6)` | 철회 확인 대기 시작 시각, nullable |
| `encryption_key_version` | `VARCHAR(32)` | 복호화 키 선택, not null |
| `status` | `VARCHAR(24)` | `CONNECTED`, `REAUTH_REQUIRED` |
| `credential_revision` | `BIGINT` | 자격 교체 경쟁 비교, not null |
| `connected_at` | `TIMESTAMP(6)` | not null |
| `created_at` | `TIMESTAMP(6)` | not null |
| `updated_at` | `TIMESTAMP(6)` | not null |

- `uk_notion_connections_user` 제약이 활성 워크스페이스 하나를 최종 보장한다. `workspace_id`는 unique가 아니므로 서로 다른 NalQ 사용자가 같은 워크스페이스를 승인할 수 있다.
- `user_id` FK는 기존 계정 생명주기 미확정을 지켜 `ON DELETE RESTRICT`로 둔다.
- `workspace_name`이 `null`인 정상 OAuth 응답도 저장한다. 연결 상태 API도 `null`을 그대로 반환하고 클라이언트가 일반적인 연결 표시를 사용한다.
- Notion 토큰 응답에 만료 시각이 없으므로 `expires_at`을 추측해 저장하지 않는다.
- Notion 사용자 ID·이름·이메일·avatar, `bot_id`, OAuth 원시 응답과 페이지 목록은 저장하지 않는다.
- 연결 해제는 연결 행을 `SELECT ... FOR UPDATE`로 잠근 뒤 그 revision의 최신 token에 Notion revoke를 수행한다. revoke `200`이면 같은 잠금 안에서 revision이 바뀌지 않았음을 확인하고 행을 삭제한다. revoke 응답 유실·timeout처럼 성공 여부가 불명확하면 같은 token을 `POST /v1/oauth/introspect`로 한 번 확인한다. `active=false`면 삭제하고, `active=true`이거나 introspection 결과를 확인할 수 없으면 행을 보존하고 `503`을 반환한다.
- 구현이 외부 revoke 동안 DB 잠금을 해제하도록 바뀌면 삭제 전에 행과 `credential_revision`을 다시 읽는다. revision이 달라졌으면 새 token까지 revoke·비활성 확인한 뒤에만 행을 삭제하며, 최신 자격의 철회를 확인하지 못한 상태에서 로컬 행만 삭제하지 않는다.

## 5. 토큰 암호화와 키 주입

- 암호화는 Java JCA의 `AES/GCM/NoPadding`과 256-bit key를 사용한다.
- access·refresh token을 각각 독립적으로 암호화하고 매 암호화마다 12-byte CSPRNG nonce를 새로 만든다. GCM tag는 암호문에 포함한다.
- AAD는 버전이 붙은 결정적 encoding으로 `userId`, `workspaceId`, `ACCESS|REFRESH`를 묶어 서로 다른 행·용도에 암호문을 옮겨 사용하는 것을 막는다.
- 개발은 키 링을 환경변수로, 운영은 비밀 관리 서비스가 환경에 주입한 키 링을 사용한다. 기본 쓰기 버전과 복호화 가능한 이전 버전을 구분한다.
- 환경 구성은 키 원문을 예시·로깅하지 않고 시작 시 키 길이·버전 중복·기본 버전 존재를 검증한다.
- 복호화된 평문은 Notion HTTP request를 만드는 짧은 범위에서만 사용하고 DTO·exception·일반 로그에 넣지 않는다.

## 6. OAuth state와 callback

- `POST /api/v1/integrations/notion/authorizations`는 NalQ Bearer 사용자를 확인하고 256-bit 수준의 CSPRNG state를 만든다.
- Redis key는 state 원문의 SHA-256 digest를 사용하고, value에 `userId`, 서버 allowlist로 확정한 복귀 대상, 의도(`CONNECT|REAUTHORIZE`), 생성 시각과 승인 시작 시점의 nullable `workspaceId`·`credentialRevision` snapshot을 둔다. `pageId`나 편집 본문은 넣지 않는다.
- TTL은 15분이며 callback은 Redis 7 `GETDEL` 또는 동일한 원자 연산으로 state를 한 번만 소비한다. 만료·없음·재사용은 자격 교환 전에 거절한다. 이때 요청 query에서 복귀 주소를 추론하지 않고 환경별 고정 `oauthFailureReturnUri`에 `outcome=failed&error=NOTION_CONNECTION_REQUIRED`를 붙여 복귀시킨다. 이 callback 문맥의 코드는 연결 행 유무가 아니라 유효한 승인 흐름을 계속할 수 없다는 뜻이며, 클라이언트는 연결 상태 재조회 결과에 맞춰 새 승인을 시작한다.
- `returnUri`는 임의 URL을 받지 않고 환경별 정확한 allowlist 값만 선택한다. callback은 Bearer 인증 대신 소비한 state의 `userId`로 소유자를 확정하고 그 계정이 존재하는지 다시 확인한다.
- callback은 state를 먼저 읽어 소유 사용자를 확인하고, 연결 행이 아직 없는 최초 연결도 직렬화할 수 있도록 해당 `users` 행을 잠근 다음 Redis `GETDEL`로 state를 원자 소비한다. 그 뒤 연결 snapshot과 현재 행을 비교한다. `CONNECT`는 현재 행이 여전히 없을 때만, `REAUTHORIZE`는 같은 `workspaceId`와 `credentialRevision`의 행이 남아 있을 때만 자격 교환·저장을 계속한다. 연결 해제도 같은 사용자 행 잠금 안에서 미소비 state를 무효화하므로, 해제·다른 callback·자격 교체 뒤 늦은 callback은 자격 교환·저장을 진행하지 않는다.
- callback 복귀 query에는 `outcome=connected|cancelled|failed`와 필요한 공개 NalQ 오류 code만 둔다. Notion authorization code·token·state·원시 오류는 포함하지 않는다.
- 새 워크스페이스 최초 연결은 새 행을 만든다. 기존 행이 있는 재인증·접근 페이지 추가는 응답 `workspace_id`가 같을 때만 자격과 표시 이름을 교체한다.
- 기존 행이 있는데 다른 `workspace_id`가 오면 새 자격을 기존 연결 자격과 분리된 pending revocation 필드에 암호화해 보존하고 revoke를 시도한다. revoke 성공 또는 introspection의 명시적 `active=false`를 확인한 뒤 pending 값을 지우고 `outcome=failed&error=NOTION_WORKSPACE_MISMATCH`로 복귀시킨다. 철회를 확인하지 못하면 pending 값을 보존하고 `503`을 반환하며, 다음 승인 시작 또는 연결 해제에서 철회를 다시 확인하기 전에는 새 작업을 진행하지 않는다. 기존 행과 상태는 유지하고 이 오류를 기존 자격의 재인증 필요로 해석하지 않는다.

## 7. Notion HTTP client 정책

- 모든 요청은 고정 구성 `Notion-Version: 2026-03-11`과 요청별 필요 header를 사용한다. 업그레이드는 한 구성 값과 adapter 계약 테스트로 통제한다.
- 연결 timeout 3초, 개별 응답 timeout 15초, 하나의 NalQ 요청이 소비하는 Notion 총 시간 20초를 상한으로 둔다. 서비스 진입점이 공유 deadline을 만들고 JDK HTTP transport가 매 호출마다 `min(15초, 남은 시간)`을 request timeout으로 사용해 순차 Page→Markdown 호출과 refresh·재시도도 같은 예산을 소비한다.
- `429`, `529`는 `Retry-After`가 남은 20초 예산 안일 때 jitter 후 한 번만 재시도한다. 최종 NalQ `503`에는 제공자 값을 검증한 `Retry-After` 표준 header를 전달할 수 있다.
- 멱등 GET의 `500|502|503|504`는 예산 안에서 한 번만 재시도한다. 일반 `400|403|404`는 재시도하지 않는다.
- OAuth code 교환·refresh 교체처럼 일회성 자격을 소비하는 POST는 응답 유실 후 무작정 자동 재시도하지 않는다.
- 자체 분산 rate-limit 큐·일일 제한은 두지 않는다. 프론트의 진행 중 버튼 제한, OAuth state 일회성, DB unique·갱신 잠금과 Notion의 `429|529` 응답 처리로 MVP를 보호한다.
- Notion 원시 request·response body, token, authorization code, refresh 실패 원문과 학습 본문을 일반 로그에 남기지 않는다. request ID, NalQ user ID, 공개 error code, endpoint 분류, latency·status 분류만 최소 기록한다.

## 8. 자격 갱신·연결 해제 동시성

1. 서비스는 호출에 사용한 `credentialRevision`을 기억한다.
2. Notion이 `401 unauthorized`를 반환하면 연결 행을 `SELECT ... FOR UPDATE`로 다시 읽는다.
3. 행의 revision이 이미 바뀌었으면 다른 요청이 갱신한 자격을 사용해 원래 요청만 한 번 재시도한다.
4. revision이 같고 refresh token이 있으면 잠금을 소유한 요청만 Notion refresh를 호출한다. 성공 응답의 access·refresh token은 새 nonce로 암호화해 하나의 DB transaction에서 함께 교체하고 revision을 증가시킨다.
5. 갱신 성공 후 원래 Notion 요청은 한 번만 재시도한다. 다시 `401`이면 추가 refresh 루프를 만들지 않고 `REAUTH_REQUIRED`로 전이한다.
6. refresh token이 없거나 `invalid_grant`·확정적 무효 응답이면 `REAUTH_REQUIRED`를 저장한다. timeout·일시 5xx는 토큰을 삭제하지 않고 일시 장애로 변환한다.
7. 연결 해제도 같은 사용자 행 잠금과 `credential_revision` 경계를 사용한다. 갱신이 먼저 끝나면 해제가 증가한 revision의 새 token을 revoke하고, 해제가 먼저 행을 삭제하면 대기하던 갱신은 행 없음으로 종료한다. 어느 순서에서도 로컬 행 없이 유효한 새 token이 남아서는 안 된다.

외부 호출 동안 행 잠금을 유지하는 방식은 MVP의 사용자별 연결 하나·자동 재시도 한 번 범위에서 수용한다. 잠금 대기·DB transaction 시간은 계측하고, 실제 병목이 확인될 때만 분산 single-flight로 확장한다.

## 9. 페이지 목록

- NalQ `GET /api/v1/integrations/notion/pages` adapter는 Notion `POST /v1/search`를 `object=page`, `last_edited_time DESC`, `page_size=20`으로 호출한다.
- `query`는 제목 검색에만 사용하고, `cursor`는 제공자 `next_cursor`를 해석하지 않은 채 전달한다. 응답은 `pageId`, `title`, `lastEditedAt`, `nextCursor`만 포함한다.
- 일반 페이지와 data source 행 페이지를 허용하고 database·data source 객체 자체는 제외한다. 제목은 `type=title` property의 `plain_text`를 순서대로 합치며 없으면 빈 문자열이다.
- Search index는 즉시·전체 결과를 보장하지 않으므로 서버·DB 목록 cache를 두지 않고 cursor 없는 새로고침을 지원한다.
- 서버 batch는 20개로 고정하고 클라이언트의 한 화면 5개·10개 분할은 후속 UI 명세가 소유한다.
- Notion 링크 폴백은 클라이언트가 공식 URL 마지막의 32 hex page UUID를 추출·정규화해 기존 import API의 `pageId`로 보낸다. 서버는 URL을 fetch하거나 저장하지 않고 Notion Page API 권한 결과만 신뢰한다.

## 10. 단일 페이지 가져오기

임시 draft·import job·DB transaction을 만들지 않고 다음 순서로 동기 처리한다.

1. 현재 `userId`의 `CONNECTED` 행을 읽고 token을 복호화한다.
2. Notion `GET /v1/pages/{pageId}`로 현재 접근과 최신 제목을 확인한다. 목록에서 본 제목을 request로 받지 않는다.
3. Page 응답의 `type=title` property `plain_text`를 합친다. 비어 있으면 기본 제목을 만들지 않고 `""`를 사용한다.
4. Notion `GET /v1/pages/{pageId}/markdown?include_transcript=false`를 호출하고 공식 응답의 `markdown`, `truncated`, `unknown_block_ids`를 provider 중립 모델로 옮긴다.
5. `truncated=true`, 비어 있지 않은 `unknown_block_ids` 또는 Notion이 만든 `<unknown ...>`이 있으면 추가 회수 없이 전체를 실패시킨다. 편집 상태로 쓸 부분 `content`는 반환하지 않는다.
6. 이미지·동영상·오디오·PDF·파일 본체와 만료 URL을 제외하되 caption·alt text는 일반 텍스트로 유지한다.
7. fenced·inline code 밖에서 enhanced Markdown 줄바꿈으로 쓰인 정확한 `<br>` 토큰만 Markdown hard break인 두 공백과 newline으로 치환한다. 코드 영역의 `<br>` 리터럴, 제목 같아 보이는 첫 `#` 행, 기타 enhanced Markdown tag·링크는 추측해 제거하거나 바꾸지 않는다.
8. 의도적으로 제외한 요소 때문에 content가 비어도 가져오기는 성공이다. 성공 응답은 `sourceType=NOTION`, `title`, `content`만 반환한다.
9. 본문·제목을 20,000·255자로 자르지 않는다. 학습자료 저장 API가 최종 길이·비어 있지 않음을 다시 검증한다.

변환기는 Notion enhanced Markdown의 외부 tag와 fenced·inline code 경계를 fixture test로 고정한다. 단순 문자열 정책으로 확정된 `<br>` 치환은 별도 함수로 두고, 미디어 제거·`<unknown>` 판정은 사용자 code sample을 잘못 변환하지 않도록 구조 인지 테스트를 둔다.

## 11. 공개 오류 변환

| 조건 | HTTP / code | 서버 후속 |
| --- | --- | --- |
| 연결 행 없음 또는 callback state 유실·만료·재사용 | 요청 API `400` 또는 callback `302`의 `NOTION_CONNECTION_REQUIRED` | Notion 자격 호출을 시작하지 않으며, 클라이언트가 연결 상태 재조회 후 현재 상태에 맞는 승인을 새로 시작 |
| refresh 불가·재동의 필요 | `409 NOTION_REAUTH_REQUIRED` | 행은 `REAUTH_REQUIRED`, token·workspace 의미 보존 |
| 기존 연결의 워크스페이스와 callback 승인 워크스페이스가 다름 | callback `302`의 `NOTION_WORKSPACE_MISMATCH` | 새 자격은 저장하지 않고 기존 행·상태 보존 |
| 페이지 없음·비공유·접근 불가 | `400 NOTION_PAGE_NOT_ACCESSIBLE` | 부분 제목·본문 없음 |
| truncation·unknown·변환 완전성 미확인 | `400 NOTION_CONTENT_INCOMPLETE` | 부분 content 없음 |
| timeout·rate limit·Notion 일시 5xx·revoke 미확인 | `503 NOTION_TEMPORARILY_UNAVAILABLE` | 재시도 가능 상태 보존 |
| NalQ Bearer 없음·무효 | `401 AUTH_005` | Notion 재인증으로 변환하지 않음 |

Notion 원시 HTTP status·error code·message와 stack trace를 공개 응답에 넣지 않는다. 클라이언트는 HTTP status만이 아니라 안정 NalQ `error.code`로 폴백을 결정한다.

## 12. 구성과 비밀 경계

- Public OAuth client ID, client secret, authorize URL, API base URL, callback URI, frontend return allowlist, state를 복구할 수 없을 때 사용할 고정 `oauthFailureReturnUri`, Notion API version을 외부 구성으로 둔다.
- client secret·AES key ring은 secret 값이며 예시 `.env.example`에 원문·실제 형식 값을 넣지 않는다. 구성 오류는 시작 시 빠르게 실패시킨다.
- OAuth 토큰 요청은 Notion client ID·secret의 HTTP Basic 인증을 사용하고, 페이지 호출은 복호화한 Bearer access token을 사용한다.
- Public connection capability는 `read_content`만 활성화하고 update·insert·comment·user information capability를 요청하지 않는다.

## 13. 테스트 우선 검증

구현 시 기준으로 삼는 Notion 공식 계약은 [Authorization](https://developers.notion.com/guides/get-started/authorization), [Create a token](https://developers.notion.com/reference/create-a-token), [Refresh a token](https://developers.notion.com/reference/refresh-a-token), [Revoke a token](https://developers.notion.com/reference/revoke-token), [Introspect a token](https://developers.notion.com/reference/introspect-token), [Search](https://developers.notion.com/reference/post-search), [Retrieve a page as Markdown](https://developers.notion.com/reference/retrieve-page-markdown), [Status codes](https://developers.notion.com/reference/status-codes)다. Provider fixture는 이 문서의 요청·응답 nullable과 오류 code를 기준으로 만든다.

### 실패 테스트 먼저

- 연결 상태·nullable `workspaceName`, OAuth 시작·callback·페이지 목록·해제·import의 공개 요청·응답·오류 계약
- 다른 사용자 연결 접근 차단과 `user_id` unique
- OAuth state 만료·일회 소비·재사용·임의 return URI 거절과 state 유실 시 고정 안전 URI 복귀
- 연결 해제 또는 다른 자격 교체 뒤 늦게 도착한 callback이 연결을 다시 만들거나 최신 자격을 덮지 않음
- 다른 워크스페이스 callback이 기존 행을 덮지 않고 `NOTION_WORKSPACE_MISMATCH`로 복귀함
- revoke 응답 유실 뒤 introspection `active=false`면 로컬 삭제, `active=true`·확인 실패면 보존
- AES-GCM round trip, 매번 다른 nonce, AAD·tag 변조 실패, 알 수 없는 key version 실패
- 동시 `401`에서 refresh 호출·자격 교체가 한 번만 일어나고 모든 소비자가 같은 새 자격을 사용함
- refresh와 연결 해제가 동시에 실행되어도 최신 token 철회 확인 전 행을 삭제하지 않고 유효한 자격을 고아로 남기지 않음
- `429|529` Retry-After, GET 5xx, timeout, 재시도 상한과 총 20초 예산
- 최신 순 20개·cursor·제목 검색·빈 제목·일반 페이지·data source 행 투영
- Page 제목 후 Markdown의 순차 호출, `include_transcript=false`
- 제목·본문 무자르기, 빈 content 성공, 미디어 URL 제거·caption 유지, 코드 영역 밖 `<br>` 치환, 링크·enhanced tag와 코드 영역 리터럴 유지
- `truncated|unknown_block_ids|<unknown ...>`의 구조 인지 판정과 부분 응답 없는 전체 실패
- 평문 token·OAuth code·state·Notion 원시 오류·본문 로그 비노출

### 검증 명령

- Docker 불필요: `server/gradlew fastTest`
- MySQL·Redis Testcontainers: `server/gradlew integrationTest`
- 전체 서버: `server/gradlew test`

Windows에서는 각 명령의 `server/gradlew` 대신 `server/gradlew.bat`을 사용한다.

외부 Notion은 port fixture·가상 HTTP server로 대체하고 실제 OAuth 자격·네트워크에 의존하지 않는다. MySQL 제약과 Redis 원자 소비만 `integration` tag로 검증한다.

## 14. 열린 질문과 후속

- API·DB·암호화·재시도·변환 구현을 막는 기술 질문은 없다.
- 페이지 선택 화면의 5개·10개 표시, 링크 폴백·오류 UX 문구, Markdown renderer, 마이페이지 연동 관리와 네이티브 앱 OAuth는 각 소유 문서의 후속 검토다.
- 미디어 가져오기를 추가할 때는 임시 Notion URL을 저장하지 않고 자체 객체 저장소로 복사하는 별도 계약을 먼저 정한다.

## 15. 변경 이력

| 날짜 | 변경 | 결정자 |
| --- | --- | --- |
| 2026-09-01 | Notion Public OAuth·1인 1 워크스페이스·Markdown 일회성 복사와 서버 안전 경계 설계 | 사용자 확정 |
| 2026-09-01 | Redis state peek-lock-GETDEL, 사용자 행 callback 직렬화, AES-GCM 자격 원장, 남은 시간 기반 HTTP transport와 fake 경계 구현 | 서버 구현 |

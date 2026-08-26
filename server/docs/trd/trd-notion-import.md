---
document_type: trd
status: proposed
scope: server
---

# [TRD · Server] Notion 단일 페이지 일회성 복사

- 상태: 공식 API 검증을 반영한 구현 제안, 외부 호출 미구현
- 대상: Notion 연결·페이지 탐색과 `POST /api/v1/learning-material-imports/notion`
- 제품 정책: [학습자료 만들기 PRD](../../../docs/prd/prd-content-import.md)
- 공유 계약: [학습자료·퀴즈 API 계약](../../../docs/contracts/contract-api-quiz-learning.md)
- 공식 문서 검증일: 2026-08-26
- 고정 API 버전 제안: `2026-03-11`

## 1. 책임과 제품 전제

이 문서는 로그인한 OpenMD 사용자가 자신의 Notion workspace를 연결하고, 접근을 허용한 페이지 중 하나를 선택해 제목·본문·변환 경고를 한 번 복사하는 서버 구조를 제안한다. OAuth와 외부 API 호출은 아직 구현하지 않는다.

다음 제품 전제는 이미 확정됐다.

- 복사는 한 페이지·일회성이다. 성공 응답은 학습자료, 서버 draft 또는 import job을 만들지 않는다.
- 사용자가 복사 결과를 확인·수정한 뒤 기존 `POST /api/v1/learning-materials`로 저장한다.
- 저장 뒤 Notion 원본과 자동·수동 동기화하지 않는다.
- 공개 복사 요청은 OpenMD의 페이지 선택 단계에서 얻은 불투명 `pageId`만 받는다.
- Notion 원문 응답, OAuth code, token, 내부 블록 타입과 파싱 예외를 공개 응답·일반 로그에 남기지 않는다.

## 2. 공식 문서로 확인한 핵심 사실

1. OpenMD처럼 여러 사용자가 각자의 workspace를 연결하는 서비스는 Notion **Public connection의 OAuth 2.0** 흐름이 맞다. Internal connection의 고정 token은 한 workspace의 내부 자동화에 적합하므로 제품 운영 방식으로 사용하지 않는다.
2. Notion 승인 화면의 page picker는 페이지 접근 권한을 부여한다. callback에는 임시 `code`와 `state`만 오며, token 응답에도 사용자가 선택한 페이지 ID 목록은 없다.
3. 연결 후 `POST /v1/search`로 해당 token이 접근할 수 있는 페이지를 조회하고, OpenMD 화면에서 가져올 페이지 한 개를 다시 선택해야 한다.
4. Search 결과는 즉시 일관적이거나 완전한 목록이라고 보장되지 않는다. 연결 직후 결과가 비어도 권한 실패로 단정하지 않고 다시 조회할 수 있어야 한다.
5. 페이지 본문은 `GET /v1/blocks/{block_id}/children`의 cursor를 끝까지 따라가야 한다. `has_children=true`인 블록은 하위 children을 별도 요청으로 재귀 조회해야 한다.
6. Notion API는 버전 header를 요구한다. 버전을 자동으로 최신화하지 않고 서버 설정에 고정하고, 버전 상승은 fixture와 계약 테스트를 통과한 뒤 배포한다.

근거는 문서 끝의 [공식 자료](#12-공식-자료)에 연결한다.

## 3. 사용자 흐름과 서버 API 제안

```text
OpenMD 로그인
  -> Notion 연결 시작
  -> Notion 승인 화면에서 workspace와 공유할 페이지 범위 선택
  -> OAuth callback에서 token 저장
  -> OpenMD가 접근 가능한 페이지를 Search API로 조회
  -> 사용자가 OpenMD에서 페이지 한 개 선택
  -> 페이지 메타데이터와 모든 하위 블록 읽기
  -> 제목·일반 텍스트·warnings 반환
  -> 사용자가 수정 후 기존 학습자료 생성 API로 저장
```

OAuth와 페이지 탐색을 위한 경로명은 아직 공유 계약에 없는 **서버 제안**이다.

| OpenMD 경계 | 책임 | 공개 형태 제안 |
| --- | --- | --- |
| `POST /api/v1/notion/authorizations` | 현재 사용자에게 바인딩한 일회용 `state` 생성, Notion 승인 URL 반환 | JSON 응답 |
| `GET /api/v1/notion/oauth/callback` | `code`·`state` 검증, token 교환·저장, 허용된 프론트 경로로 이동 | 브라우저 redirect |
| `GET /api/v1/notion/connection` | 연결 여부와 workspace 표시 정보 조회 | token 없는 JSON 응답 |
| `GET /api/v1/notion/pages` | 접근 가능한 page 제목 검색·cursor 페이지 이동 | JSON 응답 |
| `DELETE /api/v1/notion/connection` | Notion token 철회 시도 후 로컬 연결 제거 | 빈 성공 응답 |
| `POST /api/v1/learning-material-imports/notion` | 선택한 `pageId`를 한 번 읽어 편집용 제목·본문·경고 반환 | 기존 계약 유지 |

페이지 탐색 응답은 `pageId`, `title`, `lastEditedTime`, `nextCursor`, `hasMore`까지만 노출한다. Notion access token, workspace 내부 URL, 원본 property 전체와 외부 응답은 전달하지 않는다. Search cursor는 해석하지 않는 불투명 문자열로 왕복한다.

## 4. OAuth 연결

### 4.1 연결 시작과 state

- 서버가 `client_id`, 고정 `redirect_uri`, `response_type=code`, `owner=user`, 무작위 `state`로 승인 URL을 만든다.
- `state` 원문은 브라우저가 Notion에 전달하고, 서버에는 SHA-256 digest를 key로 현재 `userId`, 허용된 복귀 경로와 만료 시각을 Redis에 둔다.
- 기존 Redis 인프라를 재사용하되 OAuth state 저장소는 별도 port로 분리한다. 값은 일회성 소비이며 짧은 TTL을 둔다. 정확한 TTL은 설정값으로 두고 구현 PR에서 확정한다.
- callback은 `state`가 없거나 만료됐거나 이미 소비됐거나 현재 흐름과 일치하지 않으면 token 교환을 시도하지 않는다.
- 사용자가 승인을 취소하면 Notion의 `error`를 내부 실패로 정규화하고 token이나 연결 행을 만들지 않는다.
- 복귀 경로는 서버 allowlist 안의 상대 경로만 저장한다. 요청이 보낸 임의 URL로 redirect하지 않는다.

### 4.2 code 교환과 token lifecycle

- callback 서버만 `POST https://api.notion.com/v1/oauth/token`을 호출한다.
- 요청은 `client_id:client_secret`의 HTTP Basic 인증을 사용하고 `grant_type=authorization_code`, `code`, 설정된 `redirect_uri`를 보낸다.
- 성공 시 `access_token`, `refresh_token`, `bot_id`, `workspace_id`, 표시용 workspace 정보를 저장한다. token 응답에는 선택한 페이지 목록이 없으므로 여기서 곧바로 `pageId`를 정하지 않는다.
- refresh 응답은 새 access token과 새 refresh token을 함께 반환하므로 둘을 한 DB transaction에서 원자적으로 교체한다. 부분 교체는 금지한다.
- Notion 문서에는 token 만료 시각 필드가 명시돼 있지 않다. 임의 만료 시각을 만들지 않고 인증 실패 시 한 번 refresh한 뒤 원 요청을 한 번만 재시도한다. refresh도 실패하면 연결을 `REAUTH_REQUIRED`로 전환한다.
- 연결 해제는 Notion revoke endpoint 호출을 시도한 뒤 로컬 token material을 제거한다. Notion 장애 때문에 사용자가 로컬 연결을 해제하지 못하게 하지는 않는다.

### 4.3 capability

첫 구현은 Notion connection capability를 **Read content만** 요청한다. 페이지 생성·수정, 댓글, 사용자 정보와 이메일 권한은 요구하지 않는다.

## 5. 연결 저장 모델 제안

사용자 한 명당 활성 Notion workspace 연결 하나를 기본값으로 제안한다. 여러 workspace 동시 연결이 필요해지기 전까지 UI와 token 선택 규칙을 단순하게 유지한다. 이 cardinality는 제품 확정 전 제안이다.

```text
notion_connections
- id
- user_id                 UNIQUE, FK users.id
- workspace_id
- workspace_name          nullable, 표시용
- workspace_icon          nullable, 표시용
- bot_id
- access_token_ciphertext
- refresh_token_ciphertext
- encryption_key_version
- status                  CONNECTED | REAUTH_REQUIRED
- connected_at
- updated_at
```

- token은 복호화 가능한 인증 암호화 방식으로 저장하고 암호문과 key version만 DB에 둔다. 암호화 key는 환경 변수 또는 운영 secret manager에서 주입하며 DB·Git에 저장하지 않는다.
- 현재 서버에는 외부 token 암호화 경계가 없으므로 실제 migration보다 먼저 `NotionTokenCipher` port와 key rotation 방식을 구현해야 한다.
- 재연결이 같은 사용자에게 성공하면 기존 연결을 새 token pair와 workspace 정보로 교체한다. 기존 token revoke는 best effort로 수행한다.
- OAuth client ID·secret, redirect URI, API base URL, `Notion-Version`, timeout과 traversal budget은 설정으로 분리한다.

## 6. 페이지 탐색

- 서버는 `POST /v1/search`에 `filter.object=page`, 선택적 title query, `sort.direction=descending`, `sort.timestamp=last_edited_time`, `page_size`와 `start_cursor`를 보낸다.
- OpenMD 응답의 cursor는 Notion `next_cursor`를 그대로 의미 없는 문자열로 취급한다.
- title은 property 이름을 `title`로 가정하지 않는다. page properties 중 `type=title`인 값을 찾고 rich text의 `plain_text`를 순서대로 이어 만든다. 제목이 없으면 사용자 선택을 가능하게 하는 중립 표시명을 응답 단계에서 적용하되 저장 제목을 자동 확정하지 않는다.
- Search는 접근 가능한 페이지 전체를 반드시 열거하지 않으며 연결 직후 색인이 늦을 수 있다. 빈 결과·찾지 못함을 즉시 권한 철회로 바꾸지 않고 같은 조건으로 새로고침할 수 있게 한다.
- 첫 구현은 URL 붙여넣기와 임의 URL fetch를 제공하지 않는다. 따라서 SSRF 대상 URL 자체를 공개 입력으로 받지 않는다.

## 7. 페이지 읽기와 텍스트 변환

### 7.1 읽기

1. `GET /v1/pages/{page_id}`로 page metadata와 제목 property를 읽는다.
2. page ID를 block ID로 사용해 `GET /v1/blocks/{page_id}/children?page_size=100`을 호출한다.
3. `has_more=true`이면 `next_cursor`를 그대로 `start_cursor`에 넣어 끝까지 읽는다.
4. 각 block의 원래 순서를 보존하고 `has_children=true`이면 같은 children endpoint를 재귀 호출한다.
5. 정의된 총 시간·block 수·깊이 budget 중 하나를 넘으면 부분 성공으로 속이지 않고 복사 요청 전체를 실패시킨다.

Notion 공식 가이드는 큰 페이지를 읽을 때 비동기 구조를 권장하지만, 제품 정책은 import job과 서버 draft를 제외한다. 따라서 MVP는 동기 호출을 유지하되 반드시 유한한 traversal budget을 적용한다. 정확한 timeout·최대 block 수·깊이는 실제 fixture와 rate-limit 환경을 계측한 구현 PR에서 확정하며, 제한을 넘긴 본문을 조용히 잘라 성공시키지 않는다.

### 7.2 변환

- paragraph, heading 1~3, bulleted/numbered list, to-do, quote, callout, code, toggle과 divider를 문서 순서대로 일반 텍스트로 만든다.
- rich text는 `plain_text`, 줄바꿈과 링크 표시 문자열을 보존한다. 색상·폰트 같은 표현 속성은 버린다.
- `unsupported`, child page/database, embed, bookmark와 file·image·video 같은 media는 외부 URL을 다운로드하지 않는다. 읽을 수 있는 caption이 있으면 표시하고, 손실 가능성이 있으면 `UNSUPPORTED_BLOCK_OMITTED` 경고를 한 번 합성한다.
- 20,000자를 넘는 결과도 서버에서 임의로 자르지 않는다. 현재 계약대로 편집용 결과와 초과 경고를 반환할 수 있으며, 최종 저장 API가 `MATERIAL_002`를 검증한다.
- 외부 JSON DTO를 service나 controller까지 전달하지 않고 내부 `NotionPageSnapshot`으로 변환한 뒤 순수 renderer에 넘긴다.

## 8. 서버 의존 방향

```text
NotionAuthorizationController
  -> NotionAuthorizationService
       -> NotionOAuthClient (port)
       -> NotionConnectionRepository
       -> NotionTokenCipher
       -> NotionOAuthStateStore (Redis adapter)

NotionPageController
  -> NotionPageSearchService
       -> NotionSearchClient (port)
       -> NotionConnectionRepository

NotionImportController
  -> NotionImportService
       -> NotionPageReader (port, HTTP adapter)
       -> NotionConnectionRepository
       -> NotionTokenCipher
       -> NotionTextRenderer
```

서버가 Java이므로 JavaScript SDK를 전제로 하지 않고 Notion REST API를 작은 port 뒤에 둔다. Spring HTTP client의 구체 선택은 기존 서버 의존성과 테스트 용이성을 보고 구현 PR에서 정한다.

모든 Data API 요청은 `Authorization: Bearer ...`, `Notion-Version: 2026-03-11`을 보내며 JSON body가 있으면 `Content-Type: application/json`을 보낸다.

## 9. 제한·재시도·오류

- Notion은 connection 기준 평균 초당 3회와 workspace 기준 제한을 적용할 수 있다. 서버는 connection/workspace 단위 호출량을 제한하고 무제한 병렬 traversal을 금지한다.
- `429 rate_limited`와 `529`는 `Retry-After` 정수 초를 존중한다. 재시도는 전체 요청 시간 budget 안에서 제한 횟수만 수행한다.
- `401 unauthorized`는 token refresh 후 원 요청을 한 번만 재시도한다. 다시 실패하면 `REAUTH_REQUIRED`로 전환한다.
- `403 restricted_resource`와 `404 object_not_found`는 공개적으로 페이지 존재 여부를 구분하지 않고 `404 COMMON_003`으로 정규화한다.
- 잘못된 `pageId`는 `400 COMMON_001`이다.
- request·response body, token, OAuth code, `state` 원문과 Notion 오류 본문은 로그에 남기지 않는다. 운영 로그에는 OpenMD user ID, workspace ID, 요청 추적 ID, 호출 종류, 응답 status와 소요 시간만 최소로 남긴다.

현재 공유 계약은 모든 Notion 일시 장애를 `500 COMMON_999`로 숨기고 Notion 전용 코드를 금지한다. 이 상태로는 다음 두 조건을 프론트가 구분할 수 없다.

- 연결 없음·재승인 필요
- rate limit·timeout·Notion 5xx처럼 재시도 가능한 일시 장애

따라서 실제 구현 전에 `409 NOTION_001(REAUTH_REQUIRED)`와 `503 NOTION_002(TEMPORARILY_UNAVAILABLE)` 같은 안정 오류를 공유 계약에 추가할지 제품·프론트와 확정해야 한다. 확정 전에는 이 코드를 구현하지 않는다.

## 10. 테스트 전략

- OAuth service: state 발급·일회성 소비·만료·사용자 binding·취소 callback·code 교환 실패를 검증한다.
- OAuth HTTP adapter: code 교환, 새 token pair로 refresh, revoke 요청을 로컬 fake server로 검증한다.
- connection repository/cipher: token 평문이 DB·로그에 남지 않음, key version, 재연결 교체와 refresh 원자성을 검증한다.
- Search adapter: page filter, title query, cursor 왕복, 빈 결과와 index 지연 재조회 가능성을 검증한다.
- page reader: children page_size 100, cursor pagination, 중첩 children, 순서, unsupported block, 401 refresh 1회, 403/404, 429/529 `Retry-After`, timeout과 traversal budget을 검증한다.
- renderer: 고정 snapshot fixture로 제목 property 이름 비의존성, 중첩 목록, rich text 연결, media 생략과 warning 중복 제거를 순수 단위 테스트한다.
- controller: OpenMD Bearer 사용자 binding, 공통 응답 봉투, `pageId` 검증과 공개 오류만 검증한다.
- 자동화 테스트는 실제 Notion API·계정·token을 호출하지 않는다. 별도 로컬 profile의 수동 smoke test만 전용 test workspace에서 허용한다.

## 11. 구현 전 결정

| 항목 | 권장안 | 상태 |
| --- | --- | --- |
| 인증 방식 | Public connection OAuth 2.0, Read content만 요청 | 구현 제안 |
| 사용자당 workspace 수 | 활성 연결 1개 | 제품 확인 필요 |
| 페이지 선택 | OAuth picker는 권한 부여, OpenMD Search 결과에서 1개 선택 | 공식 동작에 따른 제안 |
| 큰 페이지 | 동기 처리 유지, 유한 budget 초과 시 전체 실패 | 수치 계측 필요 |
| 공개 오류 | 재승인 필요와 일시 장애를 구분하는 안정 코드 2개 | 공유 계약 결정 필요 |
| Notion API 버전 | `2026-03-11` 고정 후 의도적으로 업그레이드 | 구현 제안 |

## 12. 공식 자료

- [Authorization](https://developers.notion.com/guides/get-started/authorization): Internal/Public connection 구분, OAuth URL·callback·token·refresh 흐름
- [Public connections](https://developers.notion.com/guides/get-started/public-connections): 여러 workspace에 설치하는 Public connection의 성격
- [Connection capabilities](https://developers.notion.com/reference/capabilities): Read content 최소 권한
- [Search by title](https://developers.notion.com/reference/post-search): page filter, title query, sort와 cursor
- [Search optimizations and limitations](https://developers.notion.com/reference/search-optimizations-and-limitations): 색인 지연과 비완전성
- [Retrieve a page](https://developers.notion.com/reference/retrieve-a-page): page metadata와 properties
- [Retrieve block children](https://developers.notion.com/reference/get-block-children): children cursor pagination
- [Working with page content](https://developers.notion.com/guides/data-apis/working-with-page-content): 재귀 children 조회와 큰 페이지 처리 권고
- [Request limits](https://developers.notion.com/reference/request-limits): rate limit과 `Retry-After`
- [Status codes](https://developers.notion.com/reference/status-codes): 401·403·404·429·5xx·529 의미
- [Versioning](https://developers.notion.com/reference/versioning): `Notion-Version` 고정

## 13. 변경 이력

| 날짜 | 변경 | 결정자 |
| --- | --- | --- |
| 2026-08-26 | 단일 페이지 일회성 복사의 최소 서버 경계와 인증 선택지 초안 | 구현 전 제안 |
| 2026-08-26 | 공식 API를 검증해 Public OAuth, Search 기반 페이지 선택, token lifecycle, 재귀 조회·제한·테스트 설계로 개편 | 구현 전 제안 |

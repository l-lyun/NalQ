---
document_type: trd
status: proposed
scope: server
---

# [TRD · Server] Notion 단일 페이지 일회성 복사 경계

- 상태: 기초 설계, 외부 호출 미구현
- 대상: `POST /api/v1/learning-material-imports/notion`
- 제품 정책: [학습자료 만들기 PRD](../../../docs/prd/prd-content-import.md)
- 공유 계약: [학습자료·퀴즈 API 계약](../../../docs/contracts/contract-api-quiz-learning.md)

## 1. 책임과 확정 전제

이 문서는 Notion 페이지 하나를 요청 시점에 읽어 프론트 편집용 제목·본문·경고로 변환하는 서버 경계만 정의한다. OAuth 승인·callback과 페이지 선택 UX의 구체 흐름은 인증 방식 확정 뒤 별도 설계하며, 그전에는 실제 Notion SDK·HTTP 어댑터나 토큰 저장을 구현하지 않는다.

다음 제품 전제는 확정이다.

- 복사는 한 페이지·일회성이다. 성공 응답은 학습자료, 서버 draft 또는 import job을 만들지 않는다.
- 사용자가 응답을 확인·수정한 뒤 기존 `POST /api/v1/learning-materials`로 저장한다.
- 저장 뒤 Notion 원본과 자동·수동 동기화하지 않는다.
- 공개 API는 선택기가 반환한 불투명 `pageId`를 받는다. 전체 URL은 서버 공개 입력이 아니다.
- Notion 원문 응답, 접근 자격, 내부 블록 타입과 파싱 예외를 공개 응답·일반 로그에 남기지 않는다.

## 2. 제안 서버 경계

후속 구현은 다음 의존 방향을 사용한다.

```text
NotionImportController
  -> NotionImportService
       -> NotionAccessTokenProvider
       -> NotionPageReader (port)
            <- HttpNotionPageReader (adapter)
       -> NotionTextRenderer
```

- `NotionAccessTokenProvider`는 현재 사용자에게 사용할 자격을 제공하는 경계다. OAuth 확정 전에는 구현을 고정하지 않는다.
- `NotionPageReader`는 `pageId`로 페이지 메타데이터와 자식 블록 페이지를 읽고 외부 JSON을 내부 snapshot으로 바꾼다.
- `NotionTextRenderer`는 snapshot을 일반 텍스트와 안정적인 경고 코드로 변환하는 순수 구성요소다.
- Service는 제목 선택, 페이지네이션 반복, 제한 확인과 공개 오류 매핑을 조정하지만 DB에 저장하지 않는다.

Notion SDK를 바로 서비스에 주입하지 않고 작은 포트를 두어 SDK 교체와 네트워크 없는 테스트를 가능하게 한다. 실제 구현 전에는 SDK 의존성을 추가하지 않는다.

## 3. 페이지 ID와 URL

- API의 `pageId`는 Notion picker 또는 연결 UI가 반환한 식별자를 그대로 전달하는 불투명 문자열이다.
- 사용자가 Notion URL을 붙여넣는 UX가 필요하면 클라이언트가 URL에서 page ID를 추출하고 확인한 뒤 `pageId`만 보낸다. 서버가 임의 URL을 가져오게 하지 않아 SSRF 경계를 넓히지 않는다.
- 서버는 최소한 null·공백을 `COMMON_001`로 거절한다. 허용 길이와 하이픈 정규화는 실제 picker 출력 fixture를 확인한 뒤 확정한다.
- 리디렉션 URL, 데이터베이스 ID, workspace ID를 페이지 ID로 추측하지 않는다. 페이지가 없거나 자격으로 접근할 수 없으면 동일하게 `404 COMMON_003`으로 숨긴다.

## 4. 인증과 비밀 관리

인증 방식은 아직 제품 열린 질문이다.

| 선택지 | 용도와 영향 |
| --- | --- |
| 사용자별 Notion OAuth | 다중 사용자 제품에 적합하다. 사용자별 access token 저장·암호화·철회·재연결 정책이 필요하다. 권장 방향이지만 아직 확정하지 않는다. |
| 서버 Integration token | 한 팀 workspace의 내부 프로토타입에만 적합하다. 모든 사용자가 서버 integration에 공유된 페이지 범위를 보게 될 수 있어 현재 사용자별 권한 모델과 맞지 않는다. |

확정 전 공통 원칙은 다음과 같다.

- client secret, integration token과 사용자 access token은 Git·application YAML·테스트 fixture에 넣지 않는다.
- OAuth를 채택하면 `NOTION_CLIENT_ID`, `NOTION_CLIENT_SECRET`, redirect URI 등 애플리케이션 자격은 환경 또는 운영 secret manager로 주입하고, 사용자 token은 암호화 저장과 폐기 경계를 별도로 설계한다.
- Integration token 프로토타입을 명시적으로 선택할 때만 `NOTION_API_TOKEN`을 환경 secret으로 사용한다. 이를 사용자별 권한 구현으로 간주하지 않는다.
- API base URL, Notion API version, connect/read timeout은 일반 설정으로 분리하되 token 원문은 로그·예외·응답에 포함하지 않는다.

## 5. 읽기와 텍스트 변환 제안

첫 구현의 지원 범위는 텍스트 학습에 필요한 블록으로 제한한다.

- 페이지 property에서 제목 rich text를 순서대로 이어 제목으로 만든다.
- paragraph, heading 1~3, bulleted/numbered list, to-do, quote, callout, code와 divider의 rich text를 문서 순서대로 일반 텍스트로 변환한다.
- 링크 표시 문자열, inline code와 줄바꿈은 보존하되 색상·폰트 같은 표현 속성은 버린다.
- 자식 블록은 Notion cursor를 따라 읽고 지원 블록의 children을 문서 순서대로 재귀 처리한다.
- table, database, embed, bookmark, file·image·video, equation처럼 텍스트 손실 가능성이 큰 블록은 임의 본문을 만들지 않고 생략 또는 대체 텍스트와 `UNSUPPORTED_BLOCK_OMITTED` 경고를 반환한다.
- 20,000자를 넘는 결과도 서버에서 자르지 않는다. 프론트가 검토해 저장할 수 있도록 초과 경고를 추가하고 실제 저장 API가 `MATERIAL_002`를 최종 검증한다.

재귀 깊이, 최대 블록 수, rich text 세부 표기와 초과 경고 code는 실제 Notion fixture를 확보한 뒤 확정한다. 이 값들은 현재 제품 계약으로 승격하지 않는다.

## 6. 제한·오류·타임아웃

- 잘못된 `pageId` 입력은 `400 COMMON_001`이다.
- Notion의 object not found 또는 접근 불가는 사용자에게 존재 여부를 구분하지 않고 `404 COMMON_003`이다.
- timeout, rate limit, Notion 5xx와 응답 해석 실패에는 새 Notion 전용 공개 코드를 추가하지 않는다. 현재 안정 코드 집합에서는 `500 COMMON_999`로 감추고 사용자가 같은 페이지 복사를 재시도하게 한다.
- connect/read timeout은 외부 설정으로 제한한다. 초깃값은 실제 호출 계측 전 기술 가정이며 PR 검토에서 확정한다.
- 읽기는 멱등이지만 첫 구현에서 서버 자동 재시도는 하지 않는 방향을 기본으로 한다. 429/5xx 재시도를 넣을 경우 최대 횟수, backoff와 전체 응답 시간 예산을 먼저 정한다.
- 한 번의 요청에서 페이지·블록 조회가 정한 최대치에 도달하면 무한 순회하지 않고 검토 경고 또는 실패 중 하나를 선택해야 한다. 현재는 열린 질문이다.

## 7. 테스트 전략

- 서비스 테스트는 `NotionPageReader`와 token provider를 mock해 페이지 없음, 권한 없음, timeout, 20,000자 초과, 경고 합성을 검증한다.
- 렌더러는 고정 snapshot fixture로 지원 블록 순서, 중첩 목록, rich text 연결과 미지원 블록 경고를 순수 단위 테스트한다.
- HTTP 어댑터는 로컬 fake server로 cursor pagination, 404/429/5xx, timeout과 응답 역직렬화를 검증한다.
- 컨트롤러는 공통 응답 봉투, Bearer 소유자, `pageId` 검증과 공개 오류만 검증한다.
- 어떤 테스트도 실제 Notion API, 실제 계정이나 실제 token을 호출하지 않는다.

## 8. 구현 전 결정 필요

1. 사용자별 OAuth를 채택할지, 제한된 내부 프로토타입에 한해 Integration token을 쓸지.
2. 페이지 선택기를 웹에서 직접 제공할지, URL 붙여넣기 UX도 지원할지.
3. 최대 재귀 깊이·블록 수와 한계 도달 시 경고 반환 또는 요청 실패 정책.
4. 외부 timeout·rate limit의 공개 HTTP status를 현재 `COMMON_999`로 유지할지 후속 안정 코드를 정의할지.

이 네 결정 전에는 실제 외부 호출, OAuth callback, token persistence와 SDK 의존성을 추가하지 않는다.

## 9. 변경 이력

| 날짜 | 변경 | 결정자 |
| --- | --- | --- |
| 2026-08-26 | 단일 페이지 일회성 복사의 최소 서버 경계와 인증 선택지 초안 | 구현 전 제안 |

---
document_type: trd
status: implemented
scope: server
---

# [TRD · Server] 학습자료 생성·조회·수정 서버 설계

- 상태: 구현 동기화
- 대상: `POST /api/v1/learning-materials`, `GET /api/v1/learning-materials`, `GET/PATCH /api/v1/learning-materials/{materialId}`
- 제품 정책: [학습자료 만들기 PRD](../../../docs/prd/prd-content-import.md)
- 공유 계약: [학습자료·퀴즈 API 계약](../../../docs/contracts/contract-api-quiz-learning.md)
- 인증 경계: [브라우저 Refresh Token Cookie 설계](trd-browser-refresh-cookie.md)

## 1. 문서 책임

이 문서는 학습자료 생성·목록·상세 조회·수정의 MySQL 스키마, 서버 책임 분리, 트랜잭션, 페이지·검색 쿼리, 계산 상태, CORS·CSRF 경계와 기술 검증을 정의한다. 제목·본문·출처와 공개 상태·오류의 사용자 의미는 기능명세와 API 계약이 원장이며, 이 문서는 이를 재정의하지 않는다. Notion 외부 연동 경계는 별도 [Notion 일회성 복사 TRD](trd-notion-import.md)가 책임진다.

## 2. 결정 상태

### 확정

- 제목 입력은 필수이며 서버가 기본 제목을 만들지 않는다.
- 소유자는 요청 body가 아니라 검증된 Access Token의 현재 사용자다.
- 목록과 상세는 현재 인증 사용자가 소유한 자료만 노출하며, 타 사용자 자료도 `COMMON_003`으로 숨긴다.
- 목록은 1-based 페이지, 기본 6개·최대 20개, 제목 부분 검색과 `updatedAt DESC, id DESC` 정렬을 사용한다.
- `contentEditStatus`는 학습자료 행에 저장하지 않고 같은 자료의 `GENERATING` QuizSet 존재 여부로 계산한다.
- 수정은 학습자료 행을 비관적으로 잠근 뒤 `GENERATING` 존재 여부를 확인한다. 제목은 항상 수정 가능하고 본문만 생성 중에 차단한다.
- 학습자료 수정은 기존 QuizSet·attempt를 갱신하거나 삭제하지 않는다.
- 일반 stateless Bearer API는 Spring CSRF 검사 때문에 차단되지 않게 하고, 설정된 origin과 Access Token 인증은 유지한다.
- 브라우저 Refresh Cookie endpoint의 credentialed CORS와 정확한 `Origin` + `X-OpenMD-CSRF` guard는 유지한다.

### 구현 기준

- 제목은 앞뒤 Unicode 공백을 제거한 뒤 1~255 Unicode code point로 저장한다.
- 본문은 원문을 보존하고 Unicode code point로 최대 20,000자를 계산한다.
- `materialId`는 DB `BIGINT`를 JSON 10진 문자열로 직렬화한다.
- 생성 멱등 정보는 학습자료 행에 함께 두고 학습자료가 존재하는 동안 만료하지 않는다.

### 열린 질문

- 생성·목록·상세 조회·수정 구현을 막는 열린 질문은 없다. 삭제와 외부 Notion 호출 구현은 후속 범위다.

## 3. 범위와 비범위

### 범위

- Flyway `V2` 학습자료 테이블과 `V6` 계산 상태 컬럼 제거
- 인증 사용자 소유권과 생성·목록·상세 조회·수정 API
- 입력 정리·검증, 저장, 응답 변환
- 1-based 페이지, 제목 부분 검색과 고정 정렬
- `quiz_sets.status=GENERATING` 기반 `contentEditStatus` 계산과 목록 N+1 방지
- `Idempotency-Key` 검증, 중복·동시 생성 방지
- `COMMON_001/002/003/999`, `AUTH_005`, `MATERIAL_001/002` 변환
- 일반 API CORS header·method와 stateless Bearer CSRF 경계
- 컨트롤러·서비스·MySQL 8.4 통합 테스트

### 비범위

- 학습자료 삭제
- Notion 페이지 복사 API와 외부 Notion 호출
- 퀴즈 생성 실행, import·preview·draft
- 브라우저 Refresh Cookie 인증 방식 변경

## 4. 모듈 책임

[서버 패키지 구조](trd-package-structure.md)의 도메인 공용 DTO와 `controller`·`service`·`repository` 구분을 따른다.

- Controller: header·JSON·query parameter·path variable을 받고 현재 인증 주체를 서비스 입력으로 변환한다. HTTP status와 공통 응답 봉투만 책임진다.
- `LearningMaterialService`: 제목 정리, 본문·출처·멱등 키 검증, fingerprint 생성, 소유자 확인과 생성 트랜잭션 조정을 책임진다.
- `LearningMaterialQueryService`: 페이지 검증, 검색어 정리, 소유 자료 조회, 공개 상태 계산과 목록·상세 응답 투영을 책임진다.
- `LearningMaterialUpdateService`: 부분 수정 입력 검증, 소유 자료 잠금, 생성 중 본문 차단과 수정 후 상세 투영을 책임진다.
- Domain: `LearningMaterial`과 `SourceType`은 저장 상태와 생성 불변식을 가진다. `ContentEditStatus`는 응답 계산 enum이며 `LearningMaterial`의 영속 상태가 아니다.
- Repository: JPA 저장소와 MySQL 고유 제약 충돌을 생성 결과로 변환하고, 페이지 자료와 해당 페이지의 `GENERATING` 자료 ID를 집합 조회한다.

요청 DTO의 `sourceType`은 먼저 문자열로 받아 허용값을 검증한 뒤 domain enum으로 변환한다. 그러면 알 수 없는 enum은 필드 오류 `COMMON_001`로, JSON 자체를 읽을 수 없는 경우는 `COMMON_002`로 구분할 수 있다.

## 5. V2·V6 데이터 모델

`V2__create_learning_materials.sql`에서 학습자료 테이블을 만들고, `V6__remove_learning_material_edit_status.sql`에서 과거 설계의 `content_edit_status` CHECK와 컬럼을 제거한다. 현재 스키마는 다음과 같다.

| 컬럼 | 타입 | 규칙 |
| --- | --- | --- |
| `id` | `BIGINT` | PK, auto increment |
| `user_id` | `BIGINT` | `users.id` FK, not null |
| `title` | `VARCHAR(255)` | 정리된 제목, not null |
| `content` | `MEDIUMTEXT` | 원문, not null |
| `source_type` | `VARCHAR(16)` | `PASTE`, `NOTION` |
| `idempotency_key_hash` | `BINARY(32)` | SHA-256, 원문 키 저장 금지 |
| `request_fingerprint` | `BINARY(32)` | 의미 payload의 SHA-256 |
| `created_at` | `TIMESTAMP(6)` | not null |
| `updated_at` | `TIMESTAMP(6)` | not null |

제약과 인덱스는 다음을 적용한다.

- `fk_learning_materials_user`: `user_id -> users.id`, `ON DELETE RESTRICT`
- `uk_learning_materials_user_idempotency`: `(user_id, idempotency_key_hash)` unique
- `chk_learning_materials_source_type`: 두 공개 enum만 허용
- `CHAR_LENGTH(title)` 1~255, `CHAR_LENGTH(content)` 1~20,000 방어 제약

`utf8mb4`에서 20,000자는 byte 기준으로 `TEXT` 한도를 넘을 수 있으므로 `MEDIUMTEXT`를 사용한다. Unicode 공백뿐인 값 판정은 애플리케이션 검증이 책임지고 DB 길이 제약은 방어선으로 둔다.

## 6. 입력 정리와 생성 결과

1. `Idempotency-Key`가 공백 없는 출력 가능 ASCII 1~128자인지 확인한다.
2. `title` 앞뒤의 Unicode 공백을 제거한다. 결과가 비었거나 255 code point를 넘으면 `COMMON_001`의 `title` 필드 오류다.
3. `content`는 변경하지 않는다. Unicode 공백뿐이면 `COMMON_001`의 `content` 필드 오류다.
4. 원문 content의 code point가 20,000을 넘으면 `413 MATERIAL_002`다. 정확히 20,000자는 허용한다.
5. `sourceType`을 대소문자 변환 없이 `PASTE|NOTION`으로 검증한다. 그 외 값은 `COMMON_001`의 `sourceType` 필드 오류다.
6. 현재 사용자의 `userId`로 소유권을 정해 학습자료를 저장한다.
7. 저장된 ID는 10진 문자열, `contentLength`는 같은 code point 계산 결과로 응답한다. 새 자료에는 아직 QuizSet이 없으므로 생성 응답의 계산 상태는 `EDITABLE`이다.

Java 문자열의 UTF-16 `length()`를 글자 수로 사용하지 않고 code point count를 공통 함수로 둔다. 제목 정리, fingerprint와 응답은 모두 같은 정리 결과를 사용한다.

## 7. 목록·상세 조회와 계산 상태

### 목록 페이지와 검색

- Controller 기본값은 `page=1`, `size=6`이며 서비스는 `page >= 1`, `1 <= size <= 20`을 검증한다. 숫자로 해석할 수 없는 query parameter는 MVC 오류 경계에서 `COMMON_001`로 변환한다.
- 서비스는 `page - 1`로 `PageRequest`를 만들고 `updatedAt DESC`, `id DESC` 정렬을 항상 적용한다. 같은 수정 시각에도 `id`가 tie-breaker라 페이지 순서가 결정적이다.
- `query`는 제목·본문 저장과 같은 방식으로 앞뒤 Unicode whitespace·space character를 제거한다. 생략하거나 정리 결과가 빈 문자열이면 소유자 조건만, 그 외에는 소유자와 제목 `Containing` 조건을 적용한다.
- 전체 범위를 벗어난 양수 페이지는 Spring Data `Page`의 전체 집계를 유지한 채 빈 content를 반환하므로 `200`, `items=[]`, 기존 `totalElements`·`totalPages`가 된다.
- 목록 엔티티를 읽은 뒤 그 페이지의 material ID 목록으로 `GENERATING` QuizSet의 `learningMaterialId`를 한 번에 조회한다. 항목별 exists query를 반복하지 않으므로 목록 크기에 비례하는 N+1이 발생하지 않는다.
- 목록에는 본문을 싣지 않고 `materialId`, `title`, `sourceType`, 계산된 `contentEditStatus`, `updatedAt`만 투영한다.

### 상세와 소유권

- 상세는 `(materialId, userId)`로 한 번에 조회한다. 자료가 없거나 다른 사용자가 소유하면 모두 `COMMON_003`이다.
- `materialId < 1`은 `COMMON_001`이고, path 값을 정수로 해석할 수 없는 경우도 MVC 오류 경계에서 `COMMON_001`이다.
- 상세 응답의 `contentLength`는 생성 검증과 같은 Unicode code point 계산을 사용한다.
- 상세 자료를 찾은 뒤 같은 사용자·자료에 `GENERATING` QuizSet이 존재하는지 한 번 확인한다. 존재하면 `LOCKED_GENERATING`, 없으면 `EDITABLE`이다.

### 상태 저장 원칙

`ContentEditStatus`는 공개 응답 enum일 뿐 학습자료 테이블 컬럼이 아니다. 생성 성공·실패 시 별도 잠금 컬럼을 동기화하지 않고 `quiz_sets.status`가 `GENERATING`인지에 따라 매 조회에서 계산한다. 따라서 QuizSet이 `READY` 또는 `FAILED`로 전이하면 별도 해제 작업 없이 다음 조회부터 `EDITABLE`이다.

## 8. 부분 수정과 생성 접수 동시성

1. 요청 DTO는 Jackson setter 호출 여부를 기록해 필드 생략과 명시적 `null`을 구분한다. 둘 다 생략한 `{}`는 `COMMON_001`, 보낸 필드의 `null`은 해당 필드 오류다.
2. 보낸 값을 모두 먼저 검증한다. 제목은 생성과 같은 Unicode trim·255 code point, 본문은 원문 보존·공백 검증·20,000 code point 규칙을 쓴다.
3. `(materialId, userId)`로 학습자료 행을 `PESSIMISTIC_WRITE` 조회한다. 없거나 다른 사용자 소유면 동일하게 `COMMON_003`이다.
4. 같은 트랜잭션에서 해당 자료의 `GENERATING` QuizSet 존재 여부를 확인한다. 본문 필드가 있고 생성 중이면 어떤 필드도 적용하기 전에 `MATERIAL_001`로 전체 요청을 롤백한다.
5. 제목만 보낸 요청은 생성 중에도 적용하고 응답의 계산 상태를 `LOCKED_GENERATING`으로 반환한다. 생성 중이 아니면 제목·본문 중 보낸 값만 적용한다.
6. 수정 뒤 현재 상세 모양을 `200`으로 반환한다. `sourceType`, 생성 멱등 정보와 기존 QuizSet·question·attempt·result는 건드리지 않는다.

퀴즈 생성 접수도 같은 학습자료 행을 먼저 `PESSIMISTIC_WRITE`로 잠그고 `GENERATING` 행을 만든다. 따라서 수정과 생성 접수가 경합해도 “수정된 본문으로 생성 접수” 또는 “생성 접수가 먼저 성립해 본문 수정 거절” 중 하나로 직렬화된다. 별도 학습자료 잠금 컬럼이나 장시간 외부 호출을 포함한 트랜잭션은 필요하지 않다.

## 9. 생성 멱등성과 동시성

### 키와 fingerprint

- 키 digest: `SHA-256(UTF-8 Idempotency-Key)`
- fingerprint: 정리된 title, 원문 content, sourceType을 각각 UTF-8 길이와 값으로 framing한 뒤 SHA-256한다. 구분자 단순 연결로 인한 충돌을 피한다.
- 생성 멱등 컬럼은 `POST /api/v1/learning-materials` 전용 테이블에 있으므로 HTTP method와 정규화 path 범위가 테이블 자체로 고정된다. 다른 쓰기 endpoint는 이 unique 제약을 공유하지 않는다.
- 원문 키와 요청 본문은 DB·일반 로그에 남기지 않는다.

### 처리 순서

1. 입력 검증을 끝낸 뒤 생성 트랜잭션에서 학습자료 insert를 시도한다.
2. insert 성공 시 생성 행을 기준으로 `201 Created`를 반환한다.
3. `(user_id, idempotency_key_hash)` unique 충돌은 실패한 insert 트랜잭션을 완전히 종료한 뒤 별도 읽기 경계에서 기존 행을 조회한다. rollback-only 트랜잭션 안에서 복구 조회하지 않는다.
4. 기존 fingerprint가 같으면 기존 행으로 최초와 동일한 `201` body를 재구성한다.
5. fingerprint가 다르면 `400 COMMON_001`로 종료하고 새 행을 만들지 않는다.

MySQL unique 제약이 동시 insert를 직렬화하는 최종 방어선이다. 충돌 후 기존 행을 읽지 못하는 비정상 경쟁은 제한된 횟수로만 재조회하고, 끝내 확인할 수 없으면 `COMMON_999`로 처리한다. 입력 오류와 rollback된 생성은 키를 점유하지 않는다.

## 10. CORS와 CSRF

### 일반 stateless API

- `OPENMD_CORS_ALLOWED_ORIGINS`의 정확한 origin만 허용하고 `allowCredentials(false)`를 유지한다.
- 허용 method: `GET`, `POST`, `PATCH`, `DELETE`, `OPTIONS`
- 허용 header: `Authorization`, `Content-Type`, `Idempotency-Key`
- `OPTIONS`는 인증 없이 preflight할 수 있지만, 실제 학습자료 생성·목록·상세·수정 API는 유효한 Bearer Access Token이 없으면 `401 AUTH_005`다.
- 브라우저가 자동 첨부하지 않는 Authorization Bearer 기반 stateless API에는 CSRF token을 요구하지 않는다. Spring Security 설정은 이 요청을 CSRF로 `403` 처리하지 않아야 한다.
- 현재 서버에서는 Spring Security 기본 CSRF 검사를 `/api/v1/**`에 대해 제외한다. 이 설정은 인증·인가를 해제하지 않으며, 보호 API의 Bearer filter와 `authenticated()` 규칙은 그대로 적용한다.

### 브라우저 Cookie API 보존

`/api/v1/auth/web/**`도 Spring 기본 CSRF 대신 기존 `BrowserSessionRequestGuard`가 보호한다. 일반 API CORS로 덮어쓰지 않고, 기존 경로별 credentialed CORS, 정확한 browser origin allowlist와 `X-OpenMD-CSRF: 1` 검증이 서비스·Redis 접근 전에 실행돼야 한다. 인증을 해제하거나 wildcard origin을 추가하지 않는다.

## 11. 오류 매핑

| 조건 | HTTP / code | fields |
| --- | --- | --- |
| 제목 누락·공백·255자 초과 | `400 COMMON_001` | `title` |
| 본문 누락·공백뿐 | `400 COMMON_001` | `content` |
| 잘못된 출처 | `400 COMMON_001` | `sourceType` |
| 멱등 키 누락·형식·payload 불일치 | `400 COMMON_001` | `Idempotency-Key` |
| 읽을 수 없는 JSON | `400 COMMON_002` | 없음 |
| 본문 20,000자 초과 | `413 MATERIAL_002` | `content` 허용 |
| 빈 수정 요청 | `400 COMMON_001` | `body` |
| 생성 중 본문 수정 | `409 MATERIAL_001` | 없음 |
| `page < 1`, `size < 1`, `size > 20`, 숫자 해석 실패 | `400 COMMON_001` | 서비스 검증은 `page` 또는 `size` |
| `materialId < 1` 또는 숫자 해석 실패 | `400 COMMON_001` | 서비스 검증은 `materialId` |
| 상세·수정 자료 없음 또는 타 사용자 소유 | `404 COMMON_003` | 없음 |
| Access Token 없음·잘못됨·만료 | `401 AUTH_005` | 없음 |
| 예상하지 못한 저장 실패 | `500 COMMON_999` | 없음 |

DB 예외나 stack trace, 해시와 요청 본문을 공개 응답에 넣지 않는다. `MATERIAL_001`은 PATCH 본문 수정에서만 반환하고 생성·조회 API에는 사용하지 않는다.

## 12. 테스트 우선 구현 기준

### 컨트롤러·보안

- 인증된 요청이 `201`, 문자열 `materialId`, 정리된 title, code point `contentLength`, `EDITABLE`을 반환한다.
- body의 `userId`를 받지 않으며 Access Token 사용자로만 소유자를 정한다.
- 빈 제목·본문, 제목 256자, 잘못된 `sourceType`, malformed JSON, 본문 20,001자의 공개 오류를 구분한다.
- 허용 origin preflight가 `POST`와 세 허용 header를 반환하고, 허용되지 않은 origin은 CORS 응답을 받지 못한다.
- Bearer 학습자료 POST는 CSRF token 없이 컨트롤러에 도달한다.
- 목록·상세의 공통 응답 봉투와 문자열 `materialId`, 기본 `page=1`, `size=6`, 상세 code point 길이를 검증한다.
- PATCH가 보낸 필드만 수정하고 현재 상세 응답을 반환하며 Bearer 인증을 요구하는지 검증한다.
- 숫자로 해석할 수 없는 페이지와 material ID가 `COMMON_001`이고, 목록·상세도 Bearer 인증을 요구하는지 검증한다.
- OpenAPI에 생성·목록·상세 operation ID, Bearer security와 성공·오류 응답 schema가 노출되는지 검증한다.
- 브라우저 Refresh Cookie 변경 요청은 허용되지 않은 Origin이나 `X-OpenMD-CSRF` 누락 시 계속 `403 AUTH_009`로 차단된다.

### 서비스

- 제목 trim과 code point 경계(255/256, 20,000/20,001), emoji·줄바꿈 포함 길이를 검증한다.
- 최초 생성, 같은 키·같은 payload 재시도, 같은 키·다른 payload 충돌을 검증한다.
- rollback된 요청이 키를 점유하지 않고, 원문 키·본문이 로그에 남지 않는 경계를 검증한다.
- 페이지·크기 경계, Unicode 검색어 정리, 빈 검색어 전체 조회, 고정 정렬, 범위 밖 양수 페이지의 집계 보존을 검증한다.
- 목록 한 페이지의 `GENERATING` 자료 ID를 일괄 조회해 `EDITABLE|LOCKED_GENERATING`으로 투영하고, 상세도 같은 규칙을 사용하는지 검증한다.
- 상세가 타 사용자 자료를 `COMMON_003`으로 숨기고 emoji를 code point로 계산하는지 검증한다.
- 수정의 빈 요청·명시적 null·길이 경계·소유권을 검증한다. 생성 중 제목만 수정할 수 있고 본문을 포함한 요청은 변경 없이 `MATERIAL_001`인지 검증한다.

### MySQL 8.4 통합

- Testcontainers MySQL 8.4에 Flyway V1~V6가 적용되고 FK·CHECK·unique 제약이 실제로 동작하며 `content_edit_status` 컬럼이 남지 않는지 검증한다.
- `utf8mb4` emoji를 포함한 20,000 code point 본문이 손실 없이 저장되고 20,001자는 저장되지 않는다.
- 실제 MySQL에서 소유자·제목 검색, 페이지 집계, 고정 정렬과 `GENERATING` 상태 계산이 함께 동작하는지 검증한다.
- 서로 다른 사용자는 같은 키를 사용할 수 있고, 같은 사용자의 같은 키 동시 요청은 한 행만 남으며 두 요청이 같은 생성 결과를 관찰한다.
- 수정 뒤 기존 READY QuizSet과 attempt 상태가 그대로이며, 실제 `GENERATING` 행이 있을 때 본문만 차단되는지 검증한다.
- 테스트는 각 케이스의 사용자와 학습자료를 격리하고 외부 Notion 서비스를 호출하지 않는다.

구현 순서는 공개 실패 테스트 작성과 의도된 실패 확인, 최소 구현, 집중 테스트, 관련 서버 전체 테스트 순서다.

## 13. 변경 이력

| 날짜 | 변경 | 결정자 |
| --- | --- | --- |
| 2026-08-20 | 제목 필수와 미입력 저장 차단 | 사용자 확정 |
| 2026-08-20 | 설정 origin의 stateless Bearer API CORS/CSRF 개방과 Refresh Cookie guard 유지 | 사용자 확정 |
| 2026-08-20 | 문자 계산, BIGINT wire 형식, V2 스키마와 멱등 동시성 기본 설계 | 구현 전 설계 기본값 |
| 2026-08-26 | 목록·상세 조회, 1-based 페이지·검색·고정 정렬과 소유권 경계 구현 | 사용자 확정 |
| 2026-08-26 | V6에서 저장 잠금 컬럼 제거, QuizSet `GENERATING` 기반 계산 상태와 목록 일괄 조회 구현 | 사용자 확정 |
| 2026-08-26 | 부분 수정 API, 생성 접수와 공유하는 학습자료 행 잠금, 기존 퀴즈·풀이 불변성 구현 | 사용자 확정 |

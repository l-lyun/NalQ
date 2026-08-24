# 학습자료 생성 서버 설계

- 상태: 검토 중
- 대상: `POST /api/v1/learning-materials`
- 제품 정책: [학습자료 만들기 PRD](../../../docs/prd/prd-content-import.md)
- 공유 계약: [학습자료·퀴즈 API 계약](../../../docs/contracts/contract-api-quiz-learning.md#저장)
- 인증 경계: [브라우저 Refresh Token Cookie 설계](browser-refresh-cookie.md)

## 1. 문서 책임

이 문서는 학습자료 생성의 MySQL 스키마, 서버 책임 분리, 멱등 트랜잭션, CORS·CSRF 경계와 기술 검증을 정의한다. 제목·본문·출처의 사용자 의미와 공개 오류는 기능명세와 API 계약이 원장이며, 이 문서는 이를 재정의하지 않는다.

## 2. 결정 상태

### 확정

- 제목 입력은 필수이며 서버가 기본 제목을 만들지 않는다.
- 소유자는 요청 body가 아니라 검증된 Access Token의 현재 사용자다.
- 일반 stateless Bearer API는 Spring CSRF 검사 때문에 차단되지 않게 하고, 설정된 origin과 Access Token 인증은 유지한다.
- 브라우저 Refresh Cookie endpoint의 credentialed CORS와 정확한 `Origin` + `X-OpenMD-CSRF` guard는 유지한다.

### 제안 — 이번 구현 기본값

- 제목은 앞뒤 Unicode 공백을 제거한 뒤 1~255 Unicode code point로 저장한다.
- 본문은 원문을 보존하고 Unicode code point로 최대 20,000자를 계산한다.
- `materialId`는 DB `BIGINT`를 JSON 10진 문자열로 직렬화한다.
- 생성 멱등 정보는 학습자료 행에 함께 두고 학습자료가 존재하는 동안 만료하지 않는다.

### 열린 질문

- 이 엔드포인트 구현을 막는 열린 질문은 없다. 다른 쓰기 API의 멱등 결과 보존 기간은 공유 계약의 열린 질문으로 남는다.

## 3. 범위와 비범위

### 범위

- Flyway `V2` 학습자료 테이블
- 인증 사용자 소유권과 생성 API
- 입력 정리·검증, 저장, 응답 변환
- `Idempotency-Key` 검증, 중복·동시 생성 방지
- `COMMON_001/002/999`, `AUTH_005`, `MATERIAL_002` 변환
- 일반 API CORS header·method와 stateless Bearer CSRF 경계
- 컨트롤러·서비스·MySQL 8.4 통합 테스트

### 비범위

- 학습자료 수정·삭제와 본문 잠금 전이
- Notion 페이지 복사 API와 외부 Notion 호출
- 퀴즈 생성, import·preview·draft
- 브라우저 Refresh Cookie 인증 방식 변경

## 4. 모듈 책임

[서버 패키지 구조](package-structure.md)의 도메인 공용 DTO와 `controller`·`service`·`repository` 구분을 따른다.

- Controller: header와 JSON을 받고 현재 인증 주체를 command로 변환한다. HTTP status와 공통 응답 봉투만 책임진다.
- Service: 제목 정리, 본문·출처·멱등 키 검증, fingerprint 생성, 소유자 확인과 트랜잭션 조정을 책임진다.
- Domain: `LearningMaterial`, `SourceType`, `ContentEditStatus`와 생성 불변식을 가진다.
- Repository: JPA 저장소와 MySQL 고유 제약 충돌을 서비스 결과로 변환한다.

요청 DTO의 `sourceType`은 먼저 문자열로 받아 허용값을 검증한 뒤 domain enum으로 변환한다. 그러면 알 수 없는 enum은 필드 오류 `COMMON_001`로, JSON 자체를 읽을 수 없는 경우는 `COMMON_002`로 구분할 수 있다.

## 5. V2 데이터 모델

`V2__create_learning_materials.sql`에서 다음 테이블을 만든다.

| 컬럼 | 타입 | 규칙 |
| --- | --- | --- |
| `id` | `BIGINT` | PK, auto increment |
| `user_id` | `BIGINT` | `users.id` FK, not null |
| `title` | `VARCHAR(255)` | 정리된 제목, not null |
| `content` | `MEDIUMTEXT` | 원문, not null |
| `source_type` | `VARCHAR(16)` | `PASTE`, `NOTION` |
| `content_edit_status` | `VARCHAR(32)` | 최초 `EDITABLE` |
| `idempotency_key_hash` | `BINARY(32)` | SHA-256, 원문 키 저장 금지 |
| `request_fingerprint` | `BINARY(32)` | 의미 payload의 SHA-256 |
| `created_at` | `TIMESTAMP(6)` | not null |
| `updated_at` | `TIMESTAMP(6)` | not null |

제약과 인덱스는 다음을 적용한다.

- `fk_learning_materials_user`: `user_id -> users.id`, `ON DELETE RESTRICT`
- `uk_learning_materials_user_idempotency`: `(user_id, idempotency_key_hash)` unique
- `chk_learning_materials_source_type`: 두 공개 enum만 허용
- `chk_learning_materials_content_edit_status`: 현재 저장 가능한 상태 enum만 허용
- `CHAR_LENGTH(title)` 1~255, `CHAR_LENGTH(content)` 1~20,000 방어 제약

`utf8mb4`에서 20,000자는 byte 기준으로 `TEXT` 한도를 넘을 수 있으므로 `MEDIUMTEXT`를 사용한다. Unicode 공백뿐인 값 판정은 애플리케이션 검증이 책임지고 DB 길이 제약은 방어선으로 둔다.

## 6. 입력 정리와 생성 결과

1. `Idempotency-Key`가 공백 없는 출력 가능 ASCII 1~128자인지 확인한다.
2. `title` 앞뒤의 Unicode 공백을 제거한다. 결과가 비었거나 255 code point를 넘으면 `COMMON_001`의 `title` 필드 오류다.
3. `content`는 변경하지 않는다. Unicode 공백뿐이면 `COMMON_001`의 `content` 필드 오류다.
4. 원문 content의 code point가 20,000을 넘으면 `413 MATERIAL_002`다. 정확히 20,000자는 허용한다.
5. `sourceType`을 대소문자 변환 없이 `PASTE|NOTION`으로 검증한다. 그 외 값은 `COMMON_001`의 `sourceType` 필드 오류다.
6. 현재 사용자의 `userId`로 소유권을 정하고 `EDITABLE` 상태로 저장한다.
7. 저장된 ID는 10진 문자열, `contentLength`는 같은 code point 계산 결과로 응답한다.

Java 문자열의 UTF-16 `length()`를 글자 수로 사용하지 않고 code point count를 공통 함수로 둔다. 제목 정리, fingerprint와 응답은 모두 같은 정리 결과를 사용한다.

## 7. 멱등성과 동시성

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

## 8. CORS와 CSRF

### 일반 stateless API

- `OPENMD_CORS_ALLOWED_ORIGINS`의 정확한 origin만 허용하고 `allowCredentials(false)`를 유지한다.
- 허용 method: `GET`, `POST`, `PATCH`, `DELETE`, `OPTIONS`
- 허용 header: `Authorization`, `Content-Type`, `Idempotency-Key`
- `OPTIONS`는 인증 없이 preflight할 수 있지만, 실제 `POST /api/v1/learning-materials`는 유효한 Bearer Access Token이 없으면 `401 AUTH_005`다.
- 브라우저가 자동 첨부하지 않는 Authorization Bearer 기반 stateless API에는 CSRF token을 요구하지 않는다. Spring Security 설정은 이 요청을 CSRF로 `403` 처리하지 않아야 한다.
- 현재 서버에서는 Spring Security 기본 CSRF 검사를 `/api/v1/**`에 대해 제외한다. 이 설정은 인증·인가를 해제하지 않으며, 보호 API의 Bearer filter와 `authenticated()` 규칙은 그대로 적용한다.

### 브라우저 Cookie API 보존

`/api/v1/auth/web/**`도 Spring 기본 CSRF 대신 기존 `BrowserSessionRequestGuard`가 보호한다. 일반 API CORS로 덮어쓰지 않고, 기존 경로별 credentialed CORS, 정확한 browser origin allowlist와 `X-OpenMD-CSRF: 1` 검증이 서비스·Redis 접근 전에 실행돼야 한다. 인증을 해제하거나 wildcard origin을 추가하지 않는다.

## 9. 오류 매핑

| 조건 | HTTP / code | fields |
| --- | --- | --- |
| 제목 누락·공백·255자 초과 | `400 COMMON_001` | `title` |
| 본문 누락·공백뿐 | `400 COMMON_001` | `content` |
| 잘못된 출처 | `400 COMMON_001` | `sourceType` |
| 멱등 키 누락·형식·payload 불일치 | `400 COMMON_001` | `Idempotency-Key` |
| 읽을 수 없는 JSON | `400 COMMON_002` | 없음 |
| 본문 20,000자 초과 | `413 MATERIAL_002` | `content` 허용 |
| Access Token 없음·잘못됨·만료 | `401 AUTH_005` | 없음 |
| 예상하지 못한 저장 실패 | `500 COMMON_999` | 없음 |

DB 예외나 stack trace, 해시와 요청 본문을 공개 응답에 넣지 않는다. 생성 API는 잠금 상태를 만들지 않으므로 `MATERIAL_001`을 반환하지 않는다.

## 10. 테스트 우선 구현 기준

### 컨트롤러·보안

- 인증된 요청이 `201`, 문자열 `materialId`, 정리된 title, code point `contentLength`, `EDITABLE`을 반환한다.
- body의 `userId`를 받지 않으며 Access Token 사용자로만 소유자를 정한다.
- 빈 제목·본문, 제목 256자, 잘못된 `sourceType`, malformed JSON, 본문 20,001자의 공개 오류를 구분한다.
- 허용 origin preflight가 `POST`와 세 허용 header를 반환하고, 허용되지 않은 origin은 CORS 응답을 받지 못한다.
- Bearer 학습자료 POST는 CSRF token 없이 컨트롤러에 도달한다.
- 브라우저 Refresh Cookie 변경 요청은 허용되지 않은 Origin이나 `X-OpenMD-CSRF` 누락 시 계속 `403 AUTH_009`로 차단된다.

### 서비스

- 제목 trim과 code point 경계(255/256, 20,000/20,001), emoji·줄바꿈 포함 길이를 검증한다.
- 최초 생성, 같은 키·같은 payload 재시도, 같은 키·다른 payload 충돌을 검증한다.
- rollback된 요청이 키를 점유하지 않고, 원문 키·본문이 로그에 남지 않는 경계를 검증한다.

### MySQL 8.4 통합

- Testcontainers MySQL 8.4에 Flyway V1~V2가 적용되고 FK·CHECK·unique 제약이 실제로 동작한다.
- `utf8mb4` emoji를 포함한 20,000 code point 본문이 손실 없이 저장되고 20,001자는 저장되지 않는다.
- 서로 다른 사용자는 같은 키를 사용할 수 있고, 같은 사용자의 같은 키 동시 요청은 한 행만 남으며 두 요청이 같은 생성 결과를 관찰한다.
- 테스트는 각 케이스의 사용자와 학습자료를 격리하고 외부 Notion 서비스를 호출하지 않는다.

구현 순서는 공개 실패 테스트 작성과 의도된 실패 확인, 최소 구현, 집중 테스트, 관련 서버 전체 테스트 순서다.

## 11. 변경 이력

| 날짜 | 변경 | 결정자 |
| --- | --- | --- |
| 2026-08-20 | 제목 필수와 미입력 저장 차단 | 사용자 확정 |
| 2026-08-20 | 설정 origin의 stateless Bearer API CORS/CSRF 개방과 Refresh Cookie guard 유지 | 사용자 확정 |
| 2026-08-20 | 문자 계산, BIGINT wire 형식, V2 스키마와 멱등 동시성 기본 설계 | 구현 전 설계 기본값 |

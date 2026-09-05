---
document_type: data-contract
status: draft
scope: shared
last_updated: 2026-09-05
---

# [Data Contract] 사용자와 인증

- 소유 영역: 서버 사용자·인증 도메인
- 관련 기능명세: [이메일 기반 자체 인증](../prd/prd-local-authentication.md), [마이페이지와 계정 관리](../prd/prd-mypage-account-management.md)
- 관련 API: [인증 API](contract-api-authentication.md)

## 목적과 경계

이메일·비밀번호 인증, 가입 완료, 탈퇴와 동일 이메일 재가입에 필요한 사용자, 고유 닉네임과 필수 약관 동의 기록의 데이터 의미를 정의한다. 이메일 인증 코드, 가입 계속 자격과 Refresh Token 상태는 Redis에 TTL로 관리한다.

현재 서버는 이메일 인증 중 사용자 행을 만들지 않고 Redis 가입 계속 자격을 거쳐 최종 가입에서 닉네임·약관 동의가 포함된 `ACTIVE` 사용자 행을 생성한다. V3 이전 기존 행은 확인되지 않은 닉네임·동의를 임의로 채우지 않아 새 컬럼이 null일 수 있으며, 신규 가입 불변식과 물리적 `NOT NULL` 전환 단계를 구분한다.

## 확정된 의미 규칙

- `users.id`는 NalQ 계정과 사용자 소유 데이터를 식별하는 내부 키다. 이메일이나 향후 소셜 식별자를 소유 데이터의 키로 사용하지 않는다.
- 초기 로그인 아이디는 정규화 이메일이며 전역에서 유일하다.
- 활성 사용자의 닉네임은 NFC로 정규화한 단일 표시 값을 저장하며, 대소문자를 구분하지 않는 DB 비교와 `UNIQUE` 제약으로 전역 고유성을 보장한다.
- 비밀번호 원문, 6자리 인증 코드 원문, Refresh Token 원문은 DB·Redis·로그에 저장하지 않는다.
- 필수 서비스 이용약관과 개인정보 수집·이용 동의는 각각 동의 시점의 버전과 동의 시각을 사용자 행에 남긴다. 첫 공개 베타 운영 버전은 `2026-09-04`다.
- 탈퇴 확정 트랜잭션은 사용자 상태와 탈퇴 시각을 기록하면서 원 이메일을 더 이상 계정 식별이나 로그인에 사용할 수 없게 제거·비식별화하고, 기존 정규화 이메일의 고유값 점유를 해제한다.
- 같은 트랜잭션에서 닉네임 고유값 점유와 비밀번호 해시를 제거하고, 탈퇴 요청 ID와 최대 처리 기한을 기록한다. 직접 식별정보와 인증 자격은 후속 30일 작업까지 유지하지 않는다.
- 같은 이메일의 재가입은 탈퇴 확정 직후 허용하며 항상 새로운 `users.id`를 만든다. 이전 사용자의 학습자료, 문제 세트, 풀이·복습 기록과 필수 약관 동의 상태를 새 사용자에게 연결하거나 복사하지 않는다.
- 같은 닉네임도 탈퇴 확정 직후 다른 활성 사용자가 사용할 수 있다. 기존 탈퇴 계정과 새 계정은 이메일·닉네임으로 연결하지 않는다.
- 참조 보존을 위해 `WITHDRAWN` tombstone을 유지하더라도 원 이메일·닉네임 또는 원문으로 되돌릴 수 있는 값을 보관하지 않는다. 첫 구현은 `WITHDRAWN`에서 직접 식별·자격 컬럼을 `null`로 두고 활성 계정에만 필수값 제약을 적용한다.
- 소셜 제공자가 반환한 이메일이 기존 계정 이메일과 같다는 이유만으로 계정을 자동 연결하거나 병합하지 않는다.

## 관계 개요

```text
목표: users 단일 행에 로컬 가입 정보와 두 필수 약관 동의 기록
향후 소셜 로그인 승인 시: users 1 ── N social_accounts
Redis: 이메일 인증 상태 + 가입 계속 자격 + Refresh Token session/family 상태
```

## 물리 구조

### `users`

초기 로컬 계정 정보와 제품 사용자의 루트를 함께 저장한다. 학습자료, 문제 세트, 본 퀴즈 회차와 복습 세션은 이 테이블의 `id`를 참조한다.

| 필드 | 제안 타입 | null | 규칙 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | 아니요 | PK, identity; 현재 `BaseEntity`와 일치 |
| `email` | `VARCHAR(320)` | `WITHDRAWN`만 예 | 표시·메일 발송용 입력 값; 탈퇴 확정 시 `null` |
| `normalized_email` | `VARCHAR(320)` | `WITHDRAWN`만 예 | 로그인·중복 비교 값, `UNIQUE`; 탈퇴 확정 시 `null`로 점유 해제 |
| `password_hash` | `VARCHAR(255)` | `WITHDRAWN`만 예 | 최종 가입 요청의 비밀번호를 Argon2id로 해시; 탈퇴 확정 시 `null` |
| `nickname` | `VARCHAR(10)` 또는 동등한 유니코드 안전 타입 | 기존 전환 행·`WITHDRAWN` 예 | NFC 정규화한 표시 값, 화면상 2~10자, case-insensitive `UNIQUE`; 탈퇴 확정 시 `null` |
| `email_verified_at` | `TIMESTAMP(6)` | 신규 가입 아니요; 기존 상태에 따라 예 | `signUpToken`에 결합된 이메일 인증 시각 |
| `service_terms_version` | `VARCHAR(64)` | 신규 가입 아니요; 전환 전 기존 행 예 | 가입 시 동의한 서비스 이용약관 버전 |
| `service_terms_agreed_at` | `TIMESTAMP(6)` | 신규 가입 아니요; 전환 전 기존 행 예 | 서비스 이용약관 동의 확정 시각 |
| `privacy_terms_version` | `VARCHAR(64)` | 신규 가입 아니요; 전환 전 기존 행 예 | 가입 시 동의한 개인정보 수집·이용 버전 |
| `privacy_terms_agreed_at` | `TIMESTAMP(6)` | 신규 가입 아니요; 전환 전 기존 행 예 | 개인정보 수집·이용 동의 확정 시각 |
| `status` | `VARCHAR(32)` | 아니요 | 새 이메일 가입은 `ACTIVE`로 생성; 이후 `SUSPENDED`, `WITHDRAWN` 전이 |
| `activated_at` | `TIMESTAMP(6)` | 예 | 최초 활성화 시각 |
| `suspended_at` | `TIMESTAMP(6)` | 예 | 정지 상태일 때 설정 |
| `withdrawn_at` | `TIMESTAMP(6)` | 예 | `WITHDRAWN` 전환이 확정된 시각; 후속 데이터 처리 기준은 관련 PRD를 따른다. |
| `withdrawal_request_id` | `UUID` 또는 `CHAR(36)` | 예 | 탈퇴 요청 멱등 키; 설정되면 사용자 범위에서 불변이며 전역 `UNIQUE` |
| `withdrawal_disposal_due_at` | `TIMESTAMP(6)` | 예 | `withdrawn_at + 30일`; 보관 만료가 아니라 삭제·비식별화 완료 최대 시각 |
| `withdrawal_disposal_completed_at` | `TIMESTAMP(6)` | 예 | 연결 데이터의 운영 저장소 처리가 완료된 시각; 백업 만료 완료와 구분 |
| `created_at` | `TIMESTAMP(6)` | 아니요 | 생성 시각 |
| `updated_at` | `TIMESTAMP(6)` | 아니요 | 최종 변경 시각 |

제안 제약·인덱스:

- `UNIQUE(normalized_email)`로 활성 계정의 정규화 이메일 중복을 DB에서도 차단한다. 탈퇴 확정 시에는 원 정규화 이메일의 점유를 해제해 같은 이메일로 새 사용자 행을 만들 수 있어야 한다.
- `nickname` 저장 전 NFC 정규화를 적용하고 case-insensitive collation의 `UNIQUE(nickname)`으로 동시 최종 제출에서도 중복을 차단한다. 사전 중복 확인은 예약이 아니므로 이 제약을 대체하지 않는다.
- `ACTIVE`와 `SUSPENDED`는 이메일·정규화 이메일·비밀번호 해시를 필수로 가지며 `ACTIVE` 신규 계정은 닉네임도 필수다. `WITHDRAWN`은 네 필드를 모두 `null`로 가져야 한다. 다음 순방향 migration에서 기존 `NOT NULL` 제약과 상태 check를 이 불변식에 맞춘다.
- `withdrawal_request_id`는 `WITHDRAWN`에서 필수이고 전역 고유하다. 동일 사용자·동일 요청 ID는 최초 확정 결과를 재사용하며 다른 사용자에게 같은 ID가 사용되면 요청을 거절한다.
- `WITHDRAWN`은 `withdrawn_at`과 `withdrawal_disposal_due_at`을 필수로 가지며 후자는 전자에서 정확히 30일 뒤다. 운영 저장소 처리가 끝나면 `withdrawal_disposal_completed_at`을 기록하되 이 값만으로 백업 사본까지 삭제됐다고 간주하지 않는다.
- `nickname`은 화면에 보이는 글자 기준 2~10자이고 공백 없이 한글·영문·숫자만 허용한다. 특수문자와 이모지는 허용하지 않는다. 정확한 DB 길이는 문자셋과 grapheme 처리 검증 후 정한다.
- 비밀번호 제품 정책은 `^(?=.*[A-Za-z])(?=.*\d)(?=\S{8,64}$).+$`이며 특수문자를 강제하지 않는다.
- 이메일 인증과 프로필 입력 중에는 사용자 행이 없다. 최종 가입 트랜잭션은 `password_hash`, `email_verified_at`, 닉네임, 두 필수 동의의 버전·시각과 `activated_at`을 모두 가진 `ACTIVE` 행만 생성한다.
- V3 전환 마이그레이션은 기존 사용자에게 확인되지 않은 동의 값을 채우지 않으므로 새 프로필·동의 컬럼을 물리적으로 nullable로 추가한다. 위 필드의 신규 가입 필수 조건은 애플리케이션이 즉시 강제하고, 기존 데이터의 실제 동의 근거를 확보한 뒤 별도 감사 가능한 마이그레이션으로 `NOT NULL`을 적용한다.
- V4는 이전 가입 흐름이 남긴 `PENDING_ACTIVATION` 행을 정리한다. 학습자료가 참조하지 않는 대기 행은 삭제하고, 참조가 있는 비정상 대기 행은 내부 ID와 외래 키를 보존한 `WITHDRAWN` tombstone으로 전환하면서 원래 이메일을 다시 가입할 수 있게 해제한다. `ACTIVE`, `SUSPENDED`, 기존 `WITHDRAWN` 행은 변경하지 않는다.
- 회원 탈퇴 구현용 다음 순방향 migration은 `email`, `normalized_email`, `password_hash`의 nullable 상태와 사용자 상태 check를 위 불변식에 맞춘다. 기존 `WITHDRAWN` 행의 synthetic 이메일·닉네임·비밀번호 해시는 `null`로 정리한다.
- 기존 `WITHDRAWN` 행에는 서버가 생성한 고유 `withdrawal_request_id`와 `withdrawn_at + 30일`의 처리 기한을 backfill한다. 이미 기한이 지난 행에 연결 데이터가 남아 있으면 즉시 후속 처리 대상으로 두며, 실제 처리 증거 없이 완료 시각을 임의로 채우지 않는다.

## Redis: 이메일 인증 상태

이메일 인증용 DB 테이블이나 미완료 사용자 행을 만들지 않는다. 정규화 이메일의 keyed digest를 Redis 식별자로 사용하며 Redis 저장이나 메일 발송이 실패하면 사용자 행 롤백이나 정리가 필요하지 않다.

제안 key와 값:

```text
key: auth:email-verification:email:{emailKey}
value: codeDigest, attemptCount, issuedAt, resendAvailableAt
TTL: 10분 제안
```

- 코드는 alphabet `ABCDEFGHJKMNPQRSTUVWXYZ23456789`에서 `SecureRandom`으로 6자리를 생성한다.
- `emailKey`는 정규화 이메일의 서버 비밀키 기반 digest다. 6자리 코드는 전수대입 공간이 작으므로 단순 SHA digest가 아니라 `HMAC-SHA-256("EMAIL_VERIFICATION:" + emailKey + ":" + code)` 또는 동등하게 purpose와 이메일 식별자로 domain separation된 keyed digest만 저장한다.
- 제출 코드는 trim 후 uppercase하고 6자리 alphabet을 검증한 다음 digest를 계산한다.
- 이메일 인증 코드 상태의 Redis key와 값에는 이메일·코드 원문을 넣지 않는다. 이메일 기반 식별자와 제한 키에는 `normalized_email`의 keyed digest를 사용한다.
- 검증 실패 횟수 증가는 Lua 또는 동등한 원자 연산으로 수행하고, 5회 실패 시 key를 폐기하는 정책을 제안한다.
- 재발송은 60초 간격을 제안하며, 허용된 재발송은 새 digest로 원자 교체해 이전 코드를 즉시 무효화한다.
- 메일 전달이 실패하면 발송하려던 digest가 Redis의 현재 `codeDigest`와 일치할 때만 Lua compare-and-remove로 상태를 삭제한다. 따라서 해당 요청의 cooldown은 해제되어 즉시 재시도할 수 있고, 동시에 더 새 코드가 발급된 경우 그 상태를 잘못 삭제하지 않는다.
- 코드 일치 시 사용자 행을 만들지 않고 UUID v4 `signUpToken`을 발급한다. 클라이언트에는 원문을 한 번 반환하고 서버는 SHA-256 digest를 Redis key로 사용한다.
- 가입 계속 자격 값에는 최종 사용자 생성에 필요한 정규화 이메일, 표시 이메일과 인증 시각을 저장하고 TTL은 15분이다. `signUpToken` 원문은 DB·Redis·로그·분석 사건에 남기지 않으며 가입 완료 후 재사용할 수 없다.

## 사용자 행의 필수 약관 동의

- MVP는 별도 약관 이력 테이블을 만들지 않는다. `users` 행의 서비스 이용약관과 개인정보 수집·이용 동의 버전·시각 필드를 가입 완료와 같은 트랜잭션에서 채운다.
- API가 받은 `SERVICE_TERMS`, `PRIVACY_COLLECTION` 식별자는 각각 고정된 사용자 필드에 대응한다. 서버가 승인한 현재 버전과 정확히 일치하지 않거나 하나라도 빠지면 사용자 행을 만들지 않는다.
- 첫 배포에는 마케팅 목적 처리, 광고성 이메일, 선택 동의와 동의·철회 이력을 위한 필드를 만들지 않는다. 향후 별도 제품·법률 검토를 거쳐 기능과 목적이 승인되면 새 데이터 범위로 설계한다. 필수 약관 종류가 늘거나 재동의 이력이 필요해지는 경우도 별도 이력 모델을 다시 설계한다.

## Redis: Refresh Token RTR 상태

Refresh Token은 DB에 저장하지 않는다. 서버 형식은 `<randomSessionId>.<256-bit random secret>`를 제안하고 클라이언트에는 opaque 값으로 취급시킨다. Redis에는 secret의 SHA-256 digest와 session/family 상태만 TTL로 저장한다.

제안 구조:

```text
auth:session:{sessionId}
  userId, familyId, currentTokenDigest, status, absoluteExpiresAt

auth:refresh-used:{sessionId}:{tokenDigest}
  sessionId, familyId
  TTL: 해당 session/family의 남은 수명

auth:user-sessions:{userId}
  sessionId 집합
  TTL: 포함된 session 중 가장 늦은 절대 만료 이내
```

- 서버는 Refresh Token에서 session ID를 파싱해 `auth:session:{sessionId}`를 조회한다. session ID는 충분히 랜덤하게 만들지만 비밀이나 인증 근거로 신뢰하지 않고, 제시된 secret의 SHA-256 digest를 현재 digest와 비교한다.
- 원문 Refresh Token, secret, 이메일 원문, 비밀번호는 Redis key/value에 넣지 않는다.
- 회전 시 Lua 또는 동등한 원자 연산으로 현재 digest 확인, 이전 digest tombstone 생성, 새 digest 교체를 한 번에 처리한다.
- 동일 토큰의 동시 요청은 하나만 성공한다. 이미 사용된 digest가 tombstone에서 발견되면 재사용으로 판정하고 해당 session/family를 폐기한다.
- Redis Cluster에서는 예시의 `{sessionId}` hash tag로 Lua가 다루는 세션 key와 used tombstone key를 같은 hash slot에 둔다.
- 로그아웃은 session/family를 폐기하고 현재 토큰으로 더 이상 회전할 수 없게 한다.
- 세션 생성·폐기 시 `auth:user-sessions:{userId}`를 함께 갱신한다. 회원 탈퇴는 이 인덱스로 현재 사용자의 모든 session/family를 폐기하며 일부 실패하면 재시도한다.
- Redis 폐기 완료 여부와 관계없이 모든 보호 API와 refresh는 사용자 상태가 `ACTIVE`인지 확인한다. `WITHDRAWN` 사용자의 기존 Access Token과 Refresh Token은 남은 서명·TTL이 있어도 접근이나 갱신에 사용할 수 없다.
- 세션과 tombstone TTL은 Refresh Token 절대 만료를 넘지 않으며, 정확한 토큰 수명은 API 보안 정책에서 확정한다.

## 상태 전이

### 사용자

```text
사용자 행 없음 ── 이메일 코드 검증 성공 ──> Redis signUpToken(15분)
Redis signUpToken ── 닉네임·비밀번호·필수 동의 완료 ──> ACTIVE 사용자 생성 + session
ACTIVE ── 운영 정책 ──> SUSPENDED ── 해제 ──> ACTIVE
ACTIVE 또는 SUSPENDED ── 탈퇴 ──> WITHDRAWN
```

- 인증 코드나 `signUpToken`이 만료돼도 DB 사용자 행은 남지 않으며 새 코드를 요청해 다시 시작할 수 있다.
- V4 적용 전에 남아 있던 `PENDING_ACTIVATION` 이메일도 정리 후 새 이메일 인증 흐름으로 다시 가입할 수 있다.
- `WITHDRAWN`은 복구 가능한 계정 상태가 아니다. 동일 이메일 재가입은 새 `users.id`의 계정을 만들고 기존 사용자 소유 데이터나 동의 상태를 이어받지 않는다.
- 탈퇴 전이는 현재 비밀번호와 정확한 확인 문구 검증 뒤 조건부 갱신으로 한 번만 수행한다. 같은 `withdrawal_request_id`의 동시·반복 요청은 동일한 `withdrawn_at`과 처리 기한을 반환하며 새 전이나 정리 작업을 만들지 않는다.

## 향후 추가 제안: `social_accounts`

현재 생성·구현 대상이 아니다. 소셜 로그인을 실제 승인할 때만 다음 테이블 하나를 추가한다.

| 필드 | 제안 타입 | null | 규칙 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | 아니요 | PK, identity |
| `user_id` | `BIGINT` | 아니요 | FK → `users.id` |
| `provider` | `VARCHAR(32)` | 아니요 | 승인된 제공자 코드 |
| `provider_subject` | `VARCHAR(255)` | 아니요 | 제공자의 불변 사용자 식별자 |
| `created_at` | `TIMESTAMP(6)` | 아니요 | 연결 시각 |
| `updated_at` | `TIMESTAMP(6)` | 아니요 | 최종 변경 시각 |

- `UNIQUE(provider, provider_subject)`로 한 소셜 주체가 여러 사용자에 연결되는 것을 막는다.
- `INDEX(user_id)`로 사용자에 연결된 소셜 계정을 조회한다.
- 소셜 제공 이메일이 기존 `users.normalized_email`과 같아도 자동 병합하지 않는다. 로그인된 사용자의 명시적 연결이나 기존 자체 계정 재인증 같은 별도 증명이 필요하다.
- 소셜 전용 사용자의 가입 미완료 상태, 재진입, `password_hash` 불변식과 약관 완료 조건은 소셜 로그인 서버 작업에서 함께 결정한다.

## 삭제와 보존

- 사용자 데이터는 가능한 한 신속히, 탈퇴일로부터 최대 30일 이내에 삭제·비식별화를 완료하되 데이터 유형별 처리 방식과 법령상 별도 보존 항목은 아직 열려 있으므로 사용자 외래 키에 무조건 `CASCADE DELETE`를 두지 않는다.
- 사용자 루트 삭제는 `RESTRICT`를 기본으로 두고 명시적인 탈퇴 서비스에서 즉시 식별자 해제와 후속 삭제·비식별화 정책을 집행하는 방향을 제안한다.
- 탈퇴 서비스는 원 이메일·닉네임의 식별·고유값 점유 해제, 비밀번호 해시 제거, `WITHDRAWN` 전환과 후속 처리 기한 기록을 하나의 DB 확정 결과로 처리한다. 감사용 기록에는 이메일·닉네임 원문이나 가역값을 남기지 않는다.
- 후속 처리는 학습자료, 문제 세트, 풀이·복습 기록과 연결 데이터에서 사용자 연결을 제거하거나 비식별화하고 `withdrawal_disposal_completed_at`을 기록한다. 미완료 작업은 기한 전까지 재시도하며 기한 초과는 운영 경보 대상이다.
- 운영 백업 사본도 `withdrawn_at`부터 최대 30일 이내 삭제하거나 개인을 식별할 수 없도록 처리한다. 백업은 탈퇴 계정이나 이전 학습 기록 복구에 사용하지 않으며, 운영 DB의 완료 시각과 백업 만료 증적은 별도로 관리한다.
- 법령상 별도 보존이 확정된 데이터만 일반 탈퇴 데이터와 분리해 목적·근거·기간 동안 보존할 수 있다. 현재 구체 항목은 열려 있으며 임의 보존을 허용하지 않는다.
- Redis 이메일 인증·세션 상태는 정해진 TTL이 지나면 자동 삭제한다.

## 열린 질문

- 이메일 정규화의 정확한 규칙(유니코드, 국제화 도메인, 로컬 파트 대소문자 처리)
- 탈퇴 후 같은 소셜 제공자 식별자의 재사용 허용 시점
- Refresh Token의 최종 절대 수명과 여러 기기 세션 한도 (Access Token은 5분으로 확정)
- 닉네임 변경 이력·재사용 대기시간과 금칙어 정책

## 변경 이력

| 날짜 | 변경 | 결정자 |
| --- | --- | --- |
| 2026-09-05 | 첫 공개 베타 약관 버전 확정을 반영하고 법률 전문 미결정 항목을 제거 | 사용자 요청 및 PR #54 리뷰 |
| 2026-08-21 | 이메일 인증 중 사용자 행을 만들지 않고, 최종 ACTIVE 생성·UUID 가입 자격·단일 닉네임·사용자 행 필수 동의 저장으로 계약 변경 | 사용자 요청 |
| 2026-08-23 | 기존 PENDING_ACTIVATION 행을 삭제 또는 참조 보존 tombstone으로 정리하는 V4 전환 규칙 추가 | PR 리뷰 반영 |
| 2026-09-03 | 탈퇴 확정 시 원 이메일 식별·고유값 점유 해제, 동일 이메일 즉시 신규 가입과 새 사용자 ID·기존 데이터 비연결 원칙 추가 | 사용자 요청 |
| 2026-09-03 | 탈퇴 멱등 키, 직접 식별·자격 컬럼 null 처리, 닉네임 재사용, 전 세션·Access Token 차단과 30일 후속 처리 추적 계약 확정 | 사용자 요청 |

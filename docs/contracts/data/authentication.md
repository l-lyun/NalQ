# 데이터 계약: 사용자와 인증

- 상태: 초안
- 소유 영역: 서버 사용자·인증 도메인
- 관련 기능명세: [이메일 기반 자체 인증](../../features/01-local-auth.md)
- 관련 API: [인증 API](../api/authentication.md)

## 목적과 경계

이메일·비밀번호 인증과 가입 완료에 필요한 사용자, 고유 닉네임과 필수 약관 동의 이력의 데이터 의미를 정의한다. 이메일 인증 코드, 가입 계속 자격과 Refresh Token 상태는 Redis에 TTL로 관리한다.

현재 서버에는 닉네임·약관 이력과 가입 계속 자격이 없으며 이메일 인증 완료 시 바로 사용자를 활성화한다. 아래 닉네임·약관·2단계 상태는 2026-08-20 승인된 목표 계약으로, 서버 마이그레이션 전에는 구현된 사실로 간주하지 않는다.

## 확정된 의미 규칙

- `users.id`는 OpenMD 계정과 사용자 소유 데이터를 식별하는 내부 키다. 이메일이나 향후 소셜 식별자를 소유 데이터의 키로 사용하지 않는다.
- 초기 로그인 아이디는 정규화 이메일이며 전역에서 유일하다.
- 활성 사용자의 닉네임은 전역에서 유일하며, 표시 값과 영문 대소문자를 구분하지 않는 비교용 정규화 값을 분리한다.
- 비밀번호 원문, 6자리 인증 코드 원문, Refresh Token 원문은 DB·Redis·로그에 저장하지 않는다.
- 필수 약관 동의는 동의 시점의 약관 식별자와 버전별 이력으로 남긴다. 법률 검토 전 `MVP_PLACEHOLDER` 버전은 운영 약관 확정본으로 간주하지 않는다.
- 소셜 제공자가 반환한 이메일이 기존 계정 이메일과 같다는 이유만으로 계정을 자동 연결하거나 병합하지 않는다.

## 관계 개요

```text
목표: users 1 ── N user_term_agreements
향후 소셜 로그인 승인 시: users 1 ── N social_accounts
Redis: 이메일 인증 상태 + 가입 계속 자격 + Refresh Token session/family 상태
```

## 물리 구조

### `users`

초기 로컬 계정 정보와 제품 사용자의 루트를 함께 저장한다. 학습자료, 문제 세트, 본 퀴즈 회차와 복습 세션은 이 테이블의 `id`를 참조한다.

| 필드 | 제안 타입 | null | 규칙 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | 아니요 | PK, identity; 현재 `BaseEntity`와 일치 |
| `email` | `VARCHAR(320)` | 아니요 | 표시·메일 발송용 입력 값 |
| `normalized_email` | `VARCHAR(320)` | 아니요 | 로그인·중복 비교 값, `UNIQUE` |
| `password_hash` | `VARCHAR(255)` | 가입 대기 중 예 | 이메일 가입 완료 시 Argon2id 해시 필수; 향후 소셜 전용 사용자는 별도 불변식 필요 |
| `nickname` | `VARCHAR(10)` 또는 동등한 유니코드 안전 타입 | 가입 대기 중 예 | 활성 사용자 표시 값, 화면상 2~10자 |
| `normalized_nickname` | 유니코드 안전 문자열 | 가입 대기 중 예 | 한글 표현 정규화와 영문 대소문자 무시 비교 값, `UNIQUE` |
| `email_verified_at` | `TIMESTAMP(6)` | 예 | null이면 이메일 미인증 |
| `status` | `VARCHAR(32)` | 아니요 | 제공자 중립 상태 `PENDING_ACTIVATION`, `ACTIVE`, `SUSPENDED`, `WITHDRAWN` |
| `activated_at` | `TIMESTAMP(6)` | 예 | 최초 활성화 시각 |
| `suspended_at` | `TIMESTAMP(6)` | 예 | 정지 상태일 때 설정 |
| `withdrawn_at` | `TIMESTAMP(6)` | 예 | 탈퇴 처리 시각; 삭제 정책 확정 전 물리 삭제 금지 |
| `created_at` | `TIMESTAMP(6)` | 아니요 | 생성 시각 |
| `updated_at` | `TIMESTAMP(6)` | 아니요 | 최종 변경 시각 |

제안 제약·인덱스:

- `UNIQUE(normalized_email)`로 정규화 이메일 중복을 DB에서도 차단한다.
- `UNIQUE(normalized_nickname)`로 동시 최종 제출에서도 닉네임 중복을 차단한다. 사전 중복 확인은 예약이 아니므로 이 제약을 대체하지 않는다.
- `nickname`은 화면에 보이는 글자 기준 2~10자이고 공백 없이 한글·영문·숫자만 허용한다. 특수문자와 이모지는 허용하지 않는다. 정확한 DB 길이는 문자셋과 grapheme 처리 검증 후 정한다.
- 목표 2단계 가입에서는 이메일 인증 대기 행을 비밀번호 없이 만들 수 있으므로 `password_hash`는 대기 중 nullable이고, LOCAL 사용자의 `ACTIVE` 전환 시에는 필수다.
- 비밀번호 제품 정책은 `^(?=.*[A-Za-z])(?=.*\d)(?=\S{8,64}$).+$`이며 특수문자를 강제하지 않는다.
- `PENDING_ACTIVATION`은 이메일 인증 전과 인증 후 공통 가입정보 완료 전을 모두 포함할 수 있다. `ACTIVE`는 자체가입 기준 `password_hash`, `email_verified_at`, 닉네임과 필수 동의 이력, `activated_at`을 모두 보유해야 한다.
- 인증 대기 계정 정리 작업이 정해지면 `(status, created_at)` 인덱스를 추가한다. 단순 상태 조회만을 위한 단일 인덱스는 두지 않는다.

## Redis: 이메일 인증 상태

이메일 인증용 DB 테이블을 만들지 않는다. 사용자 행을 `PENDING_ACTIVATION`으로 먼저 커밋한 뒤 Redis 상태를 생성하므로 DB와 Redis를 한 트랜잭션처럼 묶지 않는다. Redis 저장이나 메일 발송이 실패하면 재발송 요청이 사용자 ID 기준 상태를 다시 만들 수 있다.

제안 key와 값:

```text
key: auth:email-verification:user:{userId}
value: codeDigest, attemptCount, issuedAt, resendAvailableAt
TTL: 10분 제안
```

- 코드는 alphabet `ABCDEFGHJKMNPQRSTUVWXYZ23456789`에서 `SecureRandom`으로 6자리를 생성한다.
- 6자리 코드는 전수대입 공간이 작으므로 단순 SHA digest가 아니라 서버 비밀키 기반 `HMAC-SHA-256("EMAIL_VERIFICATION:" + userId + ":" + code)` 또는 동등하게 purpose·user ID로 domain separation된 keyed digest만 저장한다.
- 제출 코드는 trim 후 uppercase하고 6자리 alphabet을 검증한 다음 digest를 계산한다.
- Redis key와 값에 이메일 원문·코드 원문을 넣지 않는다. 이메일 기반 보조 제한 키가 필요하면 `normalized_email`의 keyed digest를 사용한다.
- 검증 실패 횟수 증가는 Lua 또는 동등한 원자 연산으로 수행하고, 5회 실패 시 key를 폐기하는 정책을 제안한다.
- 재발송은 60초 간격을 제안하며, 허용된 재발송은 새 digest로 원자 교체해 이전 코드를 즉시 무효화한다.
- 메일 전달이 실패하면 발송하려던 digest가 Redis의 현재 `codeDigest`와 일치할 때만 Lua compare-and-remove로 상태를 삭제한다. 따라서 해당 요청의 cooldown은 해제되어 즉시 재시도할 수 있고, 동시에 더 새 코드가 발급된 경우 그 상태를 잘못 삭제하지 않는다.
- 코드 일치 시 `email_verified_at`을 기록하고 최종 가입에 사용할 짧은 수명의 불투명한 가입 계속 자격을 발급한다. 이 단계에서는 사용자를 활성화하지 않는다.
- 가입 계속 자격은 정규화 이메일 또는 내부 사용자 ID와 이메일 인증 결과에 묶고 Redis에 TTL로 저장한다. 원문은 DB·로그·분석 사건에 남기지 않으며 가입 완료 후 재사용할 수 없다. 정확한 TTL과 응답 유실 복구 정책은 서버 구현 전에 확정한다.

## `user_term_agreements` 목표 구조

현재 생성·구현 대상은 아니지만 가입 완료 서버 작업에서 함께 도입해야 하는 목표 구조다.

| 필드 | 제안 타입 | null | 규칙 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | 아니요 | PK, identity |
| `user_id` | `BIGINT` | 아니요 | FK → `users.id` |
| `terms_id` | `VARCHAR(64)` | 아니요 | 약관 종류의 안정적인 식별자 |
| `terms_version` | `VARCHAR(64)` | 아니요 | 사용자가 동의한 전문의 버전 |
| `agreed_at` | `TIMESTAMP(6)` | 아니요 | 동의가 확정된 시각 |
| `created_at` | `TIMESTAMP(6)` | 아니요 | 이력 생성 시각 |

- `UNIQUE(user_id, terms_id, terms_version)`로 같은 버전의 중복 이력을 막는다.
- 가입 완료 시 필요한 필수 약관의 식별자와 버전이 모두 기록돼야 사용자를 활성화한다.
- 마케팅 수신 동의는 실제 기능과 목적이 승인되기 전에는 필드나 임시 행을 만들지 않는다.

## Redis: Refresh Token RTR 상태

Refresh Token은 DB에 저장하지 않는다. 서버 형식은 `<randomSessionId>.<256-bit random secret>`를 제안하고 클라이언트에는 opaque 값으로 취급시킨다. Redis에는 secret의 SHA-256 digest와 session/family 상태만 TTL로 저장한다.

제안 구조:

```text
auth:session:{sessionId}
  userId, familyId, currentTokenDigest, status, absoluteExpiresAt

auth:refresh-used:{sessionId}:{tokenDigest}
  sessionId, familyId
  TTL: 해당 session/family의 남은 수명
```

- 서버는 Refresh Token에서 session ID를 파싱해 `auth:session:{sessionId}`를 조회한다. session ID는 충분히 랜덤하게 만들지만 비밀이나 인증 근거로 신뢰하지 않고, 제시된 secret의 SHA-256 digest를 현재 digest와 비교한다.
- 원문 Refresh Token, secret, 이메일 원문, 비밀번호는 Redis key/value에 넣지 않는다.
- 회전 시 Lua 또는 동등한 원자 연산으로 현재 digest 확인, 이전 digest tombstone 생성, 새 digest 교체를 한 번에 처리한다.
- 동일 토큰의 동시 요청은 하나만 성공한다. 이미 사용된 digest가 tombstone에서 발견되면 재사용으로 판정하고 해당 session/family를 폐기한다.
- Redis Cluster에서는 예시의 `{sessionId}` hash tag로 Lua가 다루는 세션 key와 used tombstone key를 같은 hash slot에 둔다.
- 로그아웃은 session/family를 폐기하고 현재 토큰으로 더 이상 회전할 수 없게 한다.
- 세션과 tombstone TTL은 Refresh Token 절대 만료를 넘지 않으며, 정확한 토큰 수명은 API 보안 정책에서 확정한다.

## 상태 전이

### 사용자

```text
PENDING_ACTIVATION ── 6자리 이메일 코드 검증 성공 ──> PENDING_ACTIVATION(email verified)
PENDING_ACTIVATION(email verified) ── 닉네임·필수 동의 완료 ──> ACTIVE + session
ACTIVE ── 운영 정책 ──> SUSPENDED ── 해제 ──> ACTIVE
ACTIVE 또는 SUSPENDED ── 탈퇴 ──> WITHDRAWN
```

- 인증 코드가 만료돼도 사용자는 `PENDING_ACTIVATION`을 유지하며 새 코드를 요청할 수 있다.
- `WITHDRAWN` 복구와 물리 삭제 여부는 데이터 삭제 정책이 정해지기 전 확정하지 않는다.

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

- 계정 탈퇴와 학습자료·문제·풀이의 삭제/익명화 정책이 열려 있으므로 사용자 외래 키에 무조건 `CASCADE DELETE`를 두지 않는다.
- 구현 전에는 사용자 루트 삭제를 `RESTRICT`하고 명시적인 탈퇴 서비스에서 정책을 집행하는 방향을 제안한다.
- Redis 이메일 인증·세션 상태는 정해진 TTL이 지나면 자동 삭제한다.

## 열린 질문

- 이메일 정규화의 정확한 규칙(유니코드, 국제화 도메인, 로컬 파트 대소문자 처리)
- 탈퇴 후 같은 정규화 이메일과 같은 소셜 제공자 식별자의 재사용 허용 시점
- Refresh Token의 최종 절대 수명과 여러 기기 세션 한도 (Access Token은 5분으로 확정)
- 법률 검토가 끝난 약관 식별자·버전과 전문, 동의 철회 및 보존 정책
- 닉네임 변경 이력·재사용 대기시간과 금칙어 정책
- 가입 계속 자격의 정확한 TTL과 최종 가입 응답 유실 복구 방식

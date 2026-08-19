# API 계약 초안: 이메일 기반 자체 인증

- 상태: 초안
- 소유 영역: 서버 사용자·인증
- 소비 영역: 웹·앱 클라이언트
- 관련 기능명세: [이메일 기반 자체 인증](../../features/01-local-auth.md)
- 관련 데이터: [사용자·인증 데이터](../data/authentication.md)

## 목적

6자리 이메일 인증 코드를 전제로 한 가입, 로그인, 인증 상태 갱신, 현재 세션 로그아웃과 현재 사용자 확인의 클라이언트·서버 경계를 정의한다.

## 공통 규칙

### 확인된 관례와 요구

- 현재 서버의 공통 응답 모양인 `{ "success", "data", "error" }`를 유지한다.
- 보호 API는 유효한 Access Token에서 얻은 내부 `userId`로 소유권을 판단한다. 요청 본문의 `userId`를 신뢰하지 않는다.
- 비밀번호, 이메일 인증 코드, Access/Refresh Token을 URL path/query에 넣지 않는다.
- 공개 인증 오류는 이메일 존재 여부, 비밀번호 일치 여부, 정지 여부를 불필요하게 구분하지 않는다.
- 오류의 `message`는 변경 가능한 사용자 안내이며, 클라이언트 분기는 안정적인 `error.code`만 사용한다.

### 구현에서 확정한 사항

- 기준 경로는 `/api/v1`으로 하고 JSON 요청·응답을 사용한다.
- Access Token은 HMAC SHA-256으로 서명한 JWT이며 보호 API의 `Authorization: Bearer <access token>`으로 전달한다.
- Access Token은 발급 시점부터 정확히 5분 동안 유효하다.
- 초기 API의 Refresh Token은 요청·응답 JSON 본문으로 전달한다. 네이티브 앱은 수신 즉시 OS 보안 저장소에 보관해야 한다.
- 모든 시각은 ISO 8601 UTC 문자열로 반환한다.

## 엔드포인트 목록

| 기능 | Method / Path | 인증 | 성공 상태 |
| --- | --- | --- | --- |
| 가입 요청 | `POST /api/v1/auth/sign-ups` | 불필요 | `202 Accepted` |
| 인증 메일 재발송 | `POST /api/v1/auth/email-verifications` | 불필요 | `202 Accepted` |
| 이메일 인증 완료 | `POST /api/v1/auth/email-verifications/confirm` | 이메일+6자리 코드 | `200 OK` |
| 로그인 | `POST /api/v1/auth/sessions` | 불필요 | `200 OK` |
| 인증 상태 갱신 | `POST /api/v1/auth/sessions/refresh` | Refresh Token | `200 OK` |
| 현재 세션 로그아웃 | `DELETE /api/v1/auth/sessions/current` | Refresh Token | `200 OK` |
| 현재 사용자 | `GET /api/v1/users/me` | Access Token | `200 OK` |

## 가입 요청

`POST /api/v1/auth/sign-ups`

```json
{
  "email": "learner@example.com",
  "password": "<redacted>"
}
```

- `email`: 필수, 이메일 형식, 정규화 전 최대 길이 제한 적용.
- `password`: 필수, 8~64자, 영문자·숫자 각각 1자 이상, 공백 불가, 특수문자 선택. 정규식 제안은 `^(?=.*[A-Za-z])(?=.*\d)(?=\S{8,64}$).+$`.
- 필수 약관이 확정되면 단순 boolean이 아니라 동의한 약관 식별자·버전을 요청 계약에 추가한다.

제안 응답:

```json
{
  "success": true,
  "data": {
    "verificationRequired": true
  },
  "error": null
}
```

- 같은 이메일 요청이 동시에 들어와도 `normalized_email`이 같은 사용자 둘을 만들지 않는다.
- 이메일 존재 여부 노출을 줄이기 위해 신규·인증 대기·활성 이메일에 가능한 한 같은 `202` 응답을 반환한다.
- 인증 대기 사용자에는 재발송 제한 내 새 6자리 코드를 보낼 수 있다. 활성 계정에 어떤 보안 안내 메일을 보낼지는 운영 정책으로 남긴다.

## 인증 메일 재발송

`POST /api/v1/auth/email-verifications`

```json
{
  "email": "learner@example.com"
}
```

- 존재하지 않거나 이미 활성화된 이메일이어도 같은 접수 응답을 반환한다.
- 새 코드를 발급하면 Redis에서 이전 코드를 원자적으로 무효화한다.
- 60초 안의 재발송 요청은 새 메일을 보내지 않되, 계정 열거를 막기 위해 동일한 `202` 접수 응답을 반환한다.

## 이메일 인증 완료

`POST /api/v1/auth/email-verifications/confirm`

```json
{
  "email": "learner@example.com",
  "code": "A7K9M2"
}
```

```json
{
  "success": true,
  "data": {
    "emailVerified": true,
    "nextAction": "LOGIN"
  },
  "error": null
}
```

- `code`는 trim 후 uppercase하고 alphabet `ABCDEFGHJKMNPQRSTUVWXYZ23456789`에 속하는 정확히 6자리인지 확인한다.
- 서버는 정규화 이메일로 `PENDING_ACTIVATION` 사용자를 찾고 Redis의 keyed digest, TTL, 실패 횟수를 Lua 또는 동등한 원자 연산으로 검증한다.
- 잘못된 코드 검증은 실패 횟수를 원자적으로 증가시키고, 5회 실패하면 현재 코드를 무효화한다.
- `nextAction=LOGIN`은 인증 후 명시적 로그인 제안에 따른 값이며, 자동 로그인 여부가 바뀌면 함께 갱신한다.

## 로그인

`POST /api/v1/auth/sessions`

```json
{
  "email": "learner@example.com",
  "password": "<redacted>"
}
```

제안 세션 응답:

```json
{
  "success": true,
  "data": {
    "accessToken": "<redacted>",
    "accessExpiresAt": "2026-08-19T00:00:00Z",
    "refreshToken": "<redacted>",
    "refreshExpiresAt": "2026-08-19T00:00:00Z"
  },
  "error": null
}
```

- 이메일과 비밀번호의 어느 부분이 틀렸는지 구분하지 않고 `AUTH_001`을 반환한다.
- 미인증·정지·탈퇴 계정도 이메일 또는 비밀번호 불일치와 같은 `AUTH_001`을 반환한다.
- 성공 시 서버 형식 `<randomSessionId>.<256-bit random secret>`의 Refresh Token을 발급한다. 클라이언트는 opaque하게 취급하며, Redis에는 secret의 SHA-256 digest와 session/family 상태만 TTL로 저장한다.

## 인증 상태 갱신

`POST /api/v1/auth/sessions/refresh`

```json
{
  "refreshToken": "<redacted>"
}
```

- JSON body로 Refresh Token 하나를 보낸다.
- 성공 응답은 로그인과 같은 세션 모양을 사용한다.
- Redis Lua 또는 동등한 원자 연산으로 현재 digest 확인, 사용 digest tombstone 생성, 새 digest 교체를 한 번에 수행한다.
- 한 Refresh Token에 대한 동시 요청은 하나만 성공한다. 클라이언트는 갱신 요청을 직렬화해야 한다.
- 이미 사용된 digest가 TTL tombstone에서 발견되면 해당 session/family를 폐기하고 `AUTH_005`를 반환한다.

## 현재 세션 로그아웃

`DELETE /api/v1/auth/sessions/current`

요청:

```json
{
  "refreshToken": "<redacted>"
}
```

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

- Redis의 현재 session/family를 폐기하며 이미 만료·폐기됐어도 같은 성공 결과를 반환해 멱등적으로 처리한다.
- 응답 성공 여부와 관계없이 클라이언트는 로컬 Access/Refresh Token과 개인 화면 데이터를 제거한다.
- Access Token이 자체 포함 토큰이면 로그아웃 직후 서버 차단 수준을 별도 결정해야 한다. 최소한 Refresh Token 갱신은 불가능해야 한다.

## 현재 사용자

`GET /api/v1/users/me`

```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "learner@example.com",
    "emailVerified": true,
    "status": "ACTIVE"
  },
  "error": null
}
```

- 향후 프로필·게임화 필드는 별도 기능이 확정될 때 추가한다.
- 내부 비밀번호·로그인 제공자 subject·세션 정보는 반환하지 않는다.

## 오류 응답

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH_001",
    "message": "이메일 또는 비밀번호를 확인해 주세요.",
    "fields": []
  }
}
```

| 조건 | HTTP 상태 | 안정적인 오류 코드 | 사용자 복구 |
| --- | --- | --- | --- |
| 필드 형식·비밀번호 정책 위반 | `400` | 기존 `COMMON_001` | 필드 수정 |
| 이메일 인증 코드 형식·값 오류 또는 사용됨 | `400` | `AUTH_003` | 남은 횟수 내 재입력 또는 새 코드 요청 |
| 이메일 인증 코드 만료·시도 소진 | `410` | `AUTH_004` | 새 코드 요청 |
| 이메일/비밀번호 불일치 또는 비활성 인증 | `401` | `AUTH_001` | 재입력 |
| 접근·갱신 자격 없음/잘못됨/만료 | `401` | `AUTH_005` | 갱신 또는 재로그인 |

초기 구현은 계정 상태를 구분하는 오류를 공개하지 않고 로그인 실패를 `AUTH_001`로 통일한다.

## 보안과 개인정보

- 비밀번호 해시는 Argon2id를 사용한다.
- 6자리 코드는 `SecureRandom`으로 만들고 Redis에는 서버 비밀키 기반 `HMAC-SHA-256("EMAIL_VERIFICATION:" + userId + ":" + code)` 또는 동등하게 domain separation된 keyed digest만 저장한다.
- Refresh Token의 session ID와 secret은 충분히 무작위로 만들고 Redis에는 secret의 SHA-256 digest만 저장한다. session ID는 조회 경로일 뿐 신뢰하지 않는다.
- 재발송 60초 제한 외의 IP·기기 단위 요청 제한은 운영 정책 확정 후 추가한다. 이메일을 제한 로그 키로 남겨서는 안 된다.
- 인증 성공/실패 로그에는 요청 추적 ID, 결과 코드와 필요한 최소 메타데이터만 남기고 이메일·비밀번호·토큰을 마스킹 또는 제외한다.
- 브라우저에서 쿠키를 사용하면 CSRF 방어, 허용 origin, credentialed CORS 정책을 함께 확정한다.
- 네이티브 앱의 Refresh Token은 일반 로컬 저장소가 아니라 OS 보안 저장소에 보관한다.
- 이메일 인증 코드와 Access/Refresh Token 원문을 로그, 분석 사건, Redis key/value에 남기지 않는다.

## 재시도와 중복 요청

- 가입 재시도는 정규화 이메일 unique 제약을 기준으로 사용자를 중복 생성하지 않는다.
- 인증 메일 재발송은 요청 한도 안에서 새 코드를 만들고 Redis의 이전 코드를 무효화한다.
- 이메일 인증 완료와 로그아웃은 네트워크 응답 유실 뒤 재시도해도 계정·세션 상태를 중복 전이하지 않는다.
- 갱신은 회전 때문에 일반적인 멱등 요청이 아니다. 소비자는 동시에 하나만 호출하고 실패 시 이전 자격을 무한 재시도하지 않는다.

## 열린 질문

- Refresh Token의 최종 절대 수명과 즉시 로그아웃을 위한 Access Token 차단 수준
- 메일 발송을 비동기로 처리할 때 가입·재발송 상태 조회 API가 필요한지
- 모든 기기 로그아웃 API의 초기 포함 여부

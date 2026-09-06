---
document_type: api-contract
status: draft
scope: shared
---

# [Contract] 앱 푸시 기기 등록과 웹·네이티브 메시지

- 제품 원장: [OS 푸시 PRD](../prd/prd-quiz-push-notifications.md)
- 기술 접근: [푸시 기술 설계안](../plans/plan-quiz-push-notifications.md)
- 재사용: [알림 API](contract-api-notifications.md), [알림 데이터](contract-data-notifications.md), [인증 API](contract-api-authentication.md)
- 작성 범위: Server HTTP와 App ↔ Web 의미·상태·복구. 구현 코드, DB 물리 스키마와 플랫폼 자격 설정은 포함하지 않는다.
- 상태: 사용자 합의를 바탕으로 구체화한 **구현 전 기술 초안**. 새 endpoint·오류·메시지는 아직 배포돼 있지 않다. 수치·보존 제안은 마지막 절에 별도 표시한다.

## 1. 소유권과 식별자

| 값 | 생성·보관 | 의미 |
| --- | --- | --- |
| `installationId` | Native, 무작위 UUID | 설치별 식별자. 사용자당 하나가 아님 |
| `installationKey` | Native, 32바이트 난수의 base64url, 보안 저장소 | 그 설치 등록을 변경·해제할 자격. 사용자 로그인 토큰이 아님 |
| `revision` | Server, 0부터 증가하는 정수 | 설치 상태 변경의 순서. 재등록·권한 변경·연결 변경 때 비교 |
| `bindingId` | Server, 불투명 UUID | 설치와 계정의 한 활성 연결. 계정 전환·연결 해제 후 재등록 시 새 값 |
| `operationId` | Native, UUID | 한 HTTP 변경 의도의 멱등 키. 전송 재시도에서도 동일 값 |
| `operationIssuedAt` | Native, UTC 시각 | 변경 의도 최초 생성 시각. 동일 operation 재시도에서 변경 불가 |
| `bridgeSessionId` | Native, 문서 인스턴스별 UUID | reload·renderer 재생성 전 메시지 배제 |
| `authEpoch` | Web, 현재 실행 중 단조 증가 정수 | 로그인 시작·성공, 계정 전환·로그아웃·인증 종료의 비동기 작업 격리 |

Native는 Access/Refresh Token을 받거나 사용자 인증 API를 직접 호출하지 않는다. 웹이 기존 Bearer 인증으로 등록한다. 사용자 ID·세션 ID는 서버 principal에서 결정하며 요청 body의 사용자 식별자를 신뢰하지 않는다.

설치 key는 TLS header로만 전달하고 서버에는 digest를 저장한다. native와 허용된 WebView 간 필요한 동안만 전달하며 웹 localStorage·URL·로그·분석 사건에는 기록하지 않는다. 브리지 검증 실패 또는 지원 버전 불일치 시 푸시 연결만 중단하고 기존 웹 기능은 유지한다.

이 방식은 **앱에서만 기능을 실행하는 MVP 채널 구분**이다. key·handshake·토큰 형식만으로 정식 바이너리를 입증하지 않는다. 최초 등록은 인증과 토큰 소지를 전제로 하며, App Attest/Play Integrity는 합의대로 후속 범위다.

## 2. 기기 상태 조회·등록

### 상태 조회

`GET /api/v1/push-devices/{installationId}`

- Bearer 인증 + `X-Push-Installation-Key` 필수. 신규 설치는 404를 받은 뒤 `expectedRevision=0`으로 등록한다.
- 응답: `{ revision, belongsToCurrentUser, bindingId, status, platform }`. `bindingId`는 현재 계정 연결일 때만 반환한다. 다른 계정의 ID·이메일·토큰은 반환하지 않는다.
- 설치 key가 맞으면 새 계정에서도 revision을 조회해 계정 전환 등록을 할 수 있다. key 오류와 없는 설치는 같은 404다.
- `status`: `ACTIVE | DISABLED | REVOKED`. 설치 정보와 계정 연결의 동시 변경은 원자적이다.

### 등록·갱신·계정 전환

`PUT /api/v1/push-devices/{installationId}`

- Bearer 인증 + `X-Push-Installation-Key` 필수. 아래 값은 형식 예시이며 실제 토큰이 아니다.

```json
{
  "operationId": "11111111-1111-4111-8111-111111111111",
  "operationIssuedAt": "2026-09-06T00:00:00Z",
  "expectedRevision": 0,
  "platform": "IOS",
  "provider": "EXPO",
  "pushToken": "<native-issued-token>",
  "permission": "GRANTED"
}
```

응답은 기존 `ApiResponse` envelope의 `data`에 `{ installationId, revision, bindingId, status, userId }`를 담는다. `userId`는 현재 인증 사용자이며 native의 pending 계정 분류 보조값이다. 권한 증명으로 사용하지 않는다.

- 플랫폼은 `IOS | ANDROID`, 제공자는 초기 `EXPO`, 등록 권한 상태는 `GRANTED | DENIED`다. 미결정 권한에서는 서버 등록 없이 native 권한 요청을 먼저 실행한다.
- `GRANTED`는 유효한 형식의 native-issued token 필수. `DENIED`는 기존 설치를 `DISABLED`로 변경할 때 사용하며 `pushToken`을 생략한다. 신규 거절 기기는 등록하지 않되 응답은 `status=DISABLED`, `revision=0`, `bindingId=null`인 비영속 결과로 반환한다. 비활성화 시 발송용 토큰은 제거한다.
- 같은 설치·같은 계정의 토큰 갱신은 `bindingId` 유지, `revision` 증가. 다른 계정 연결 또는 해제 후 재연결은 기존 연결을 종료하고 새 `bindingId`를 만든다.
- 한 기기의 변경으로 다른 설치의 연결은 바뀌지 않는다. `(provider, pushToken)`은 활성 연결에서 중복되지 않는다.
- 동일 토큰이 다른 설치에 이미 있으면 같은 인증 사용자 소유일 때만 그 이전 활성 토큰 연결을 대체할 수 있다. 다른 사용자 소유이면 `409 PUSH_TOKEN_CONFLICT`로 거절하고 이전 소유자 정보를 노출하지 않는다. 정상적인 같은 설치 계정 전환은 설치 key로 기존 연결을 증명하므로 허용한다.
- 신규 ID를 생성해 충돌을 반복 우회하지 않는다. 설치 key 유실과 다른 계정 소유 토큰 충돌이 함께 발생하면 자동 이관·기존 연결 비활성화를 하지 않는다. 복구 제안은 이전 계정 로그인으로 동일 사용자 이관을 완료한 뒤 로그아웃/해제하고 새 계정에서 등록하는 것이다. 이전 계정 접근이 불가능하면 계정 복구를 안내하고 푸시 등록만 보류한다. 소유 증명 challenge 추가는 이번 MVP 기본안에서 제외한다.
- 변경은 설치 단위 잠금/원자 비교로 직렬화한다. `expectedRevision`이 다르면 `409 PUSH_REVISION_CONFLICT`; 같은 key로 상태를 재조회한 뒤 **현재 사용자의 최신 의도**에 대해서만 새 operation을 만든다.
- 같은 `(installationId, operationId)`와 동일한 인증 사용자·요청 내용은 같은 결과를 반환한다. 다른 내용 또는 다른 사용자로 키를 재사용하면 `409 PUSH_OPERATION_CONFLICT`다. 저장된 응답이 현재 revision보다 오래됐으면 native는 적용하지 않는다.
- 웹·native는 설치당 변경 요청 한 개만 진행한다. 네트워크 실패에는 같은 operation을 재전송하고, 새 권한/계정 변경은 앞선 결과를 회수하거나 상태를 재조회한 뒤 처리한다.

### 변경 요청 수명과 기록 삭제

복구·보존 정책은 2026-09-06 사용자 승인이다. 다음 요청 시간 검증은 이를 구현하기 위한 기술 설계안이다.

- PUT과 revoke는 `operationIssuedAt`(ISO-8601 UTC)을 필수로 추가한다. JSON 요청에 이 필드를 포함하며 고정 예시가 아닌 실제 의도 생성 시각을 사용한다. native의 `PUSH_DEVICE`·pending revoke에도 같은 값을 보관·전달한다.
- 서버는 인증/설치 자격 검증 후 서버 시각 기준 24시간 이상 된 요청 또는 5분 넘게 미래인 요청을 `409 PUSH_OPERATION_EXPIRED`로 거절한다. 만료 검사 후 멱등 응답을 조회하며 만료 요청은 DB 상태를 변경하지 않는다. 서버 응답 `Date`를 시계 오차 진단에 사용하고 잘못된 기기 시각으로 무한 요청하지 않는다.
- 같은 operation은 요청 내용·시각을 바꾸지 않는다. 만료 후 등록은 현재 인증·권한·설치 상태를 다시 확인한 최신 의도만 새 operation으로 제출한다. 기존 설치가 404면 이전 `expectedRevision=0` 요청을 재생하지 않고 새 설치 ID/key로 등록한다. 늦은 revoke는 원래 binding에만 적용한다. 만료됐으면 새 operation으로 재시도할 수 있으나 binding을 현재 연결로 바꾸지 않는다. 404는 대상이 이미 없어 해제할 수 없는 종료 상태로 처리한다.
- 멱등 결과는 최초 처리 후 7일 보관하고 재시도로 기한을 연장하지 않는다. 요청 digest에는 operationIssuedAt을 포함한다. 비활성 설치는 `inactiveAt + 30일`에 삭제하고 no-op 해제로 inactiveAt을 갱신하지 않는다. 토큰은 비활성화 때 즉시 제거한다.
- 이 시간 검증은 30일 뒤 삭제된 설치에 과거 최초 HTTP 요청이 재생되는 것을 막는다. 인증 사용자가 시각을 바꿔 만드는 새로운 요청의 정식 앱 여부를 증명하지 않는다. 그 경계는 기존 MVP 인증·설치 소유권 범위를 따른다. 24시간 만료·30일 삭제·서버 시계 경계는 통합 테스트로 검증한다.

### 재설치·토큰 유실 복구

- 앱 bootstrap·로그인·foreground 복귀에서 권한과 Expo 토큰을 확인하고 native token 변경 listener에서도 Expo 토큰을 다시 취득한다. 네트워크 실패는 기존 연결을 지우지 않고 재시도한다. 이미 허용한 권한을 다시 동의받는 흐름은 만들지 않는다.
- 설치 ID/key가 남으면 같은 설치에 CAS 갱신한다. 토큰 변경 시 tokenVersion을 증가시키고 이전 버전의 미발송 작업은 취소한다. 이미 외부로 접수된 푸시의 회수는 보장하지 않는다.
- key가 유실되면 새 설치 ID/key를 만들며, 동일 토큰의 기존 소유자와 현재 인증 사용자가 같은 경우에만 token unique 제약과 잠금으로 기존 연결 종료·새 연결 활성화를 원자 처리한다. 다른 기기 연결은 건드리지 않는다. 과거 delivery를 새 연결로 복제하지 않는다.
- key와 토큰이 모두 바뀌면 이전 설치를 확실히 식별할 수 없으므로 새 기기로 등록한다. 추측으로 같은 계정의 다른 기기를 삭제하지 않는다. 기존 토큰의 `DeviceNotRegistered` 결과는 해당 tokenVersion에 한해 비활성화한다. 서버가 앱 삭제를 즉시 알 수 있다고 가정하지 않는다.
- 재설치에서 Expo 토큰과 SecureStore 자격의 잔존 여부를 고정 가정하지 않는다. 앱 로컬 읽음 큐·pending open까지 복원하는 기능은 아니다. [Expo FAQ](https://docs.expo.dev/push-notifications/faq/), [SecureStore](https://docs.expo.dev/versions/latest/sdk/securestore/)

## 3. 연결 해제와 로그아웃

`POST /api/v1/push-devices/{installationId}/revoke`

```json
{
  "operationId": "22222222-2222-4222-8222-222222222222",
  "operationIssuedAt": "2026-09-06T00:00:00Z",
  "bindingId": "33333333-3333-4333-8333-333333333333",
  "expectedRevision": 4
}
```

- `X-Push-Installation-Key` 필수. 이 endpoint만 사용자 Bearer를 요구하지 않는다. 로그아웃 후에도 **그 설치의 특정 연결 해제만** 재시도할 수 있도록 제한된 자격을 사용한다. 등록·조회·다른 기기 조작 권한은 주지 않는다.
- 웹은 전용 제한 클라이언트로 호출한다. 기존 공통 anonymous endpoint처럼 명시적인 보안 경로·CORS·빈도 제한을 설정한다. native는 웹이 없을 때 직접 사용자 API를 호출하지 않고 pending revoke를 보관한다.
- key가 유효하고 요청 binding이 현재 연결이면 revision 비교 후 해제한다. 이미 해제됐거나 새 binding으로 바뀐 경우 성공 no-op; 늦게 온 이전 해제가 새 연결을 해제하지 않는다. 현재 binding은 같지만 revision이 다르면 409다.
- 정상 해제는 `200` + `data: { revoked: true }`; 같은 operation을 재생하면 최초 결과를 그대로 반환한다. 자격이 유효하지만 요청 binding이 이미 현재 연결이 아니어서 변경할 상태가 없으면 `200` + `data: { revoked: false }`인 성공 no-op이며 native는 해당 pending revoke를 종료한다. 설치 key 오류와 설치 없음은 404. 결과와 무관하게 사용자 로그아웃을 무한 대기시키지 않는다.
- 로그아웃 시작 때 웹이 native에 `SESSION_ENDING`을 보내고 native가 revoke 의도를 먼저 보존한다. 보존 ACK 후 웹이 revoke와 기존 logout을 시도한다. ACK가 없거나 저장에 실패하면 제한된 대기 후 기존 logout은 진행하고 미보존 진단을 남긴다. 다음 WebView bootstrap은 로그인 여부와 관계없이 보존된 pending revoke를 먼저 처리할 수 있다.
- 계정 전환 중 이전 revoke가 늦어져도 새 PUT은 같은 설치 key와 최신 revision으로 재연결할 수 있다. 이전 binding의 pending 발송은 취소하고 오래된 revoke는 no-op다.
- 명시적 서버 logout 성공 시 연결된 `sessionId`의 설치도 해제하도록 인증 서비스에 연결한다. 앱 측 revoke는 만료된 세션·요청 실패를 보완한다. refresh 회전은 연결을 바꾸지 않으며, 세션 자연 만료만으로 기기를 해제하지 않는다.
- 탈퇴는 서버에서 그 사용자의 모든 기기를 해제한다. 서버에 해제 의도가 도달하기 전 또는 이미 외부로 넘긴 푸시의 표시 취소는 보장하지 않는다.

## 4. 단일 알림 조회와 이동

`GET /api/v1/notifications/{notificationId}` — Bearer 필수, 응답은 기존 `NotificationItem` 한 건.

- 본인 소유이고 생성 후 90일 보존 범위인 알림만 반환한다. 이 기간과 결과 필드 의미는 기존 알림 Contract를 따른다.
- 서버 조회의 `actionType`으로 성공은 `/learning/quizzes?focus={quizSetId}`, 실패는 `/learning/{materialId}/quiz`로 이동한다. 푸시에 담긴 임의 URL은 실행하지 않는다.
- `targetAvailable=false`면 `대상을 찾을 수 없어요.` 안내 후 `/notifications`로 이동하고 해당 알림을 읽음 처리한다.
- 단일 조회 자체의 404는 타인·만료·없는 알림을 구분하지 않는다. 개인 정보나 추정 목적지를 표시하지 않고 `알림을 찾을 수 없어요.` 안내 후 현재 계정 알림함으로 이동한다. 읽음 요청은 만들지 않는다. 2026-09-06 승인된 복구 정책이다.
- 단일 조회 200 뒤 목적지 조회에서 대상 404가 발생해도 대상 없음 흐름으로 복구한다. 네트워크 오류·5xx·401은 대상 삭제로 취급하지 않는다.
- native의 마지막 연결 정보로 현재 계정이 다름을 알 수 있으면 해당 open을 실행하지 않고 같은 계정 로그인까지 보류한다. 일치 정보가 없어도 최종 서버 조회는 반드시 수행한다.

## 5. 푸시 데이터

OS 제목은 알림 원장의 `targetName`, 본문은 제품 PRD의 결과 문구를 사용한다. data는 다음과 같이 **알림 조회를 시작할 최소 신호**로 사용한다.

```json
{
  "payloadVersion": 1,
  "notificationId": "44444444-4444-4444-8444-444444444444",
  "bindingId": "33333333-3333-4333-8333-333333333333"
}
```

- `bindingId`는 발송 대상 연결의 snapshot이며 로그인 사용자 ID·자격 증명은 아니다. native의 기존 연결-사용자 매핑은 계정 분류에만 쓰고 서버 소유권 확인을 대체하지 않는다.
- 같은 계정으로 재연결되면 binding이 바뀔 수 있으므로 binding 불일치만으로 같은 사용자의 과거 알림을 무조건 버리지 않는다. 보존된 매핑으로 계정을 확인하거나 서버 단일 조회로 판단한다.
- 자료 본문·문제·정답·인증 토큰은 포함하지 않는다. 퀴즈 제목은 OS 표시용이므로 로그아웃 뒤 이미 발송한 푸시에 남을 수 있다.
- 이 버전은 기존 알림 데이터의 의미를 바꾸지 않는다. 기존 Contract의 전체 FCM data 확장 예시 대신, Expo transport는 이 절의 최소 신호를 사용하고 나머지 정보는 인증 조회로 가져온다.

### 발송 유효 시간

푸시 발송 유효 시간은 제품 PRD의 결과 확정 후 1시간을 따른다. 서버는 알림의 `createdAt + 1시간`을 고정 `expiresAt`으로 사용하고 `now >= expiresAt`이면 새 전송을 시작하지 않는다. 재시도·기기별 작업 생성·재시작으로 새 1시간을 부여하지 않는다.

Expo 전송에는 동일한 절대 만료 시각을 `expiration`(Unix 초)으로 전달하고, 우선 적용되는 `ttl`을 함께 보내 만료를 덮어쓰지 않는다. 이 기한은 제공자의 미전달 메시지 만료 설정이며 이미 표시된 알림의 제거를 뜻하지 않는다. [Expo 만료 필드](https://docs.expo.dev/push-notifications/sending-notifications/#message-request-format)

이미 ticket을 받은 작업은 기한 이후에도 receipt 결과 확인·토큰 정리를 계속할 수 있지만 새 전송은 하지 않는다. 기한 직전 전송 중인 요청의 응답을 기한 이후 받으면 접수 결과를 기록하며, 응답이 늦었다는 이유로 재전송하지 않는다. 읽음 동기화와 알림함 보존은 이 발송 기한의 영향을 받지 않는다.

## 6. 브리지 envelope와 연결

```json
{
  "version": 1,
  "type": "PUSH_OPEN",
  "messageId": "55555555-5555-4555-8555-555555555555",
  "bridgeSessionId": "66666666-6666-4666-8666-666666666666",
  "authEpoch": 3,
  "payload": { "notificationId": "44444444-4444-4444-8444-444444444444", "bindingId": "33333333-3333-4333-8333-333333333333" }
}
```

- native는 검증된 웹 origin의 최상위 문서에서만 제한된 수신 통로를 설치하고 handshake를 진행한다. `HELLO` 초기 교환만 session 미설정 상태를 허용한다. 이후 양쪽은 negotiated version·session을 검증한다.
- 메시지 이름별 방향·payload schema를 검증한다. 크기 상한은 8KiB 제안. 알 수 없는 버전/종류·잘못된 JSON은 무시하고 진단 코드만 남긴다. 메시지 전체를 로그에 남기지 않는다.
- `window.ReactNativeWebView` 존재만으로 기능을 활성화하지 않는다. web-ready/hello 응답 및 허용된 기능 목록을 확인한다. bridgeSession은 문서 reload마다 바뀌고 메시지에 든 임의 코드·URL을 평가하지 않는다.
- native → web 전달에는 고정 이벤트 수신 함수를 사용하며, 문자열 직렬화·escape를 거쳐 데이터를 전달한다. 전달 payload를 실행 코드로 연결하지 않는다.

| 종류 | 방향 | payload와 완료 조건 |
| --- | --- | --- |
| `WEB_READY` | Web → App | 지원 versions. 최초/문서 재시작 시 전송 |
| `HELLO` | App → Web | 선택 version, bridgeSessionId, capabilities(`push-v1`) |
| `AUTH_STATE` | Web → App | phase(`authenticated`, `anonymous`, `bootstrapping`), authEpoch, 인증 완료 시 userId. 토큰 없음 |
| `PUSH_REGISTER_REQUEST` | Web → App | 현재 authEpoch. 앱 최초 인증·복귀 때만 요청 |
| `PUSH_DEVICE` | App → Web | 설치 ID/key, operationId, operationIssuedAt, expectedRevision, platform, token, permission. JWT는 포함하지 않음 |
| `PUSH_REGISTER_RESULT` | Web → App | 해당 operationId, 성공 응답 또는 안정 오류. native는 epoch·revision 확인 후 보관 |
| `PUSH_REGISTER_ACK` | App → Web | 해당 operationId, bindingId·revision을 native에 내구 저장 완료. 이때 등록 교환 종료 |
| `SESSION_ENDING` | Web → App | logout/withdrawal 이유, 종료 전 authEpoch. revoke 의도 보존 요청 |
| `SESSION_ENDING_ACK` | App → Web | pending revoke 내구 저장 완료 여부. 저장 실패를 성공으로 응답하지 않음 |
| `PUSH_REVOKE` | App → Web | 특정 binding의 pending revoke와 설치 key. 인증 여부와 독립인 제한 작업 |
| `PUSH_REVOKE_RESULT` | Web → App | operationId, 성공/재시도/영구 실패. 성공 시 native pending 제거 |
| `PUSH_OPEN` | App → Web | notificationId, bindingId. 선택 사건의 messageId는 재전달에서도 유지 |
| `PUSH_OPEN_ACK` | Web → App | 원래 messageId, outcome(`COMPLETED`, `UNAVAILABLE`), 처리한 계정 userId |

native는 푸시 선택을 먼저 내구 저장한다. 새 bridge session에서는 새 envelope로 재전달하되 논리 messageId는 유지한다. 로그인 전 `PUSH_OPEN`도 로그인 유도용으로 전달할 수 있지만 개인 결과 조회와 ACK는 인증 후에만 실행한다.

## 7. 계정 전환 경합과 ACK

1. 웹 작업 생성 시 `{ userId, authEpoch }`를 고정한다. API 전송 직전, refresh 이후 재전송 직전, 결과 적용 직전에 모두 현재 값과 비교한다.
2. 다른 계정이거나 epoch가 바뀌었으면 로컬 `AUTH_CONTEXT_CHANGED`로 취소한다. 기존 `protectedApi`가 재시도 때 현재 토큰을 읽는다는 이유로 이전 계정 작업을 새 토큰으로 재전송하면 안 된다. request/response interceptor 양쪽에서 검사하고, refresh도 시작 계정의 epoch가 유지될 때만 토큰을 저장한다. 같은 세션의 정상 access refresh만으로 epoch를 증가시키지는 않는다.
3. 같은 계정 재로그인 후 미처리 작업은 새 epoch로 다시 시작한다. 기존 HTTP operation의 요청 내용/사용자를 바꿔 재사용하지 않는다.
4. old native 등록 응답은 계정·session이 다르면 UI에 적용하지 않는다. 서버에 실제 변경이 반영됐을 가능성은 다음 상태 조회로 조정한다. 늦은 등록을 방지하려고 native 자격으로 임의 계정 로그인을 만들지 않는다.
5. 같은 messageId 재전달은 하나의 진행 작업 또는 완료 기록으로 합친다. ACK를 받은 뒤 native pending과 해당 SDK last response를 정리한다. 더 최신 last response를 무조건 지우지 않는다.

`COMPLETED` ACK 조건은 **목적지 데이터 확인 또는 대상 없음 안내·알림함 복구 + 읽음 의도와 open 완료 기록의 내구 저장**이다. 서버 읽음 요청 성공까지 기다리지 않는다. `UNAVAILABLE`는 인증 후 알림 자체 404의 안전한 복구와 완료 기록 저장을 뜻하며 읽음 의도가 없다.

로그인 대기, 계정 불일치, 조회 장애, 저장소 실패에는 완료 ACK를 보내지 않는다. 이후 재전달은 같은 화면에서 처리 중인 작업과 합치며 무한 `navigate`를 일으키지 않는다. 단순 메시지 수신이나 `navigate()` 호출은 완료가 아니다.

## 8. 읽음 재시도

- 웹의 내구 저장소(IndexedDB 제안)에 `{ userId, notificationId, notificationCreatedAt, queuedAt, nextAttemptAt }`를 저장한다. `(userId, notificationId)` unique. open 완료 기록과 한 트랜잭션으로 기록해 ACK 유실 뒤 중복 이동을 줄인다.
- 기존 읽음 PUT을 재사용하고 성공한 항목만 제거한다. 첫 `readAt`은 기존 서버 규칙을 유지한다. 다른 기기에서 이미 읽은 경우도 성공이다.
- 네트워크·429·5xx는 제한된 backoff, 401은 인증 복구 대기, 404는 만료/삭제로 큐 제거. 잘못된 요청 등 영구 4xx는 계속 재시도하지 않고 최소 진단을 남긴다. 화면을 되돌리거나 오류 팝업을 표시하지 않는다.
- 앱 bootstrap·foreground·online 복귀에서 현재 계정 큐만 실행한다. 로그아웃/다른 계정에서는 정지·격리하고 **같은 계정으로 돌아오면 재개**한다. 탈퇴 또는 알림 보존 기간 종료 시 제거한다. 2026-09-06 승인된 정책이다.
- 알림 생성 후 90일이라는 기존 원장 수명을 넘겨 재시도하지 않는다. 앱 재설치·사용자 저장소 삭제 뒤 복원까지 보장하지 않는다.
- 서버 성공 전 badge를 임의로 차감하지 않고, 성공 후 캐시 갱신으로 다른 화면과 맞춘다. 재시도 완료 전 잠시 unread로 보일 수 있지만 푸시 목적지 이용은 막지 않는다.

## 9. HTTP 오류

기존 오류는 기존 의미로 재사용하며 `PUSH_*`는 이번 초안의 신규 코드다.

| HTTP / code | 의미 | 소비자 행동 |
| --- | --- | --- |
| 400 `COMMON_001` | schema·토큰 형식 오류 | 재시도 중단, 진단 |
| 401 `AUTH_005` | 사용자 인증 필요 | 같은 계정 인증 복구 후 재개 |
| 404 `COMMON_003` | 설치/key 검증 실패 또는 알림 없음·타인·만료 | endpoint별 안전한 종료, 존재 여부 추측 금지 |
| 409 `PUSH_REVISION_CONFLICT` | 상태 변경 경합 | 설치 상태 재조회 후 최신 의도만 적용 |
| 409 `PUSH_OPERATION_CONFLICT` | 멱등 키의 내용·사용자 불일치 | 키를 임의 반복하지 않고 오류 진단 |
| 409 `PUSH_OPERATION_EXPIRED` | 변경 요청 유효 시간 초과 또는 미래 시각 | 시각·최신 의도·연결 상태 재확인, 동일 요청 자동 반복 금지 |
| 409 `PUSH_TOKEN_CONFLICT` | 다른 소유 연결의 동일 토큰 | 이전 계정 정보 노출·자동 탈취 없이 등록 중단 |
| 429 `PUSH_RATE_LIMITED` | 등록/해제 요청 빈도 초과 | Retry-After가 있으면 존중, backoff |
| 5xx `COMMON_999` | 일시적 서버 실패. Redis 제한 저장소 장애로 변경 요청을 안전하게 판정할 수 없는 503 포함 | 같은 operation으로 제한 재시도 |

## 10. 호환성·보존·검증 경계

- 구앱 + 신규 웹: handshake 없으면 기존 알림 기능만 사용한다. 신규 앱 + 구웹: pending 선택을 보관하되 알 수 없는 메시지를 강제로 실행하지 않는다. 둘 다 퀴즈 이용을 막지 않는다.
- 서버 변경은 endpoint 추가와 기존 읽음 API 재사용이다. 브리지 실패를 이유로 원격 웹 버전을 가정해 임의 스크립트를 주입하거나 WebView 전체를 재시작하지 않는다.
- 기기 기록·제한 해제 자격은 활성 등록을 유지하는 동안 필요하다. 비활성 설치 30일·멱등 기록 7일과 변경 요청 시간 검증은 위 수명 규칙을 따른다. revision과 요청 시간 검사로 오래된 재시도가 새 상태를 덮어쓰거나 삭제된 설치를 복원하지 못하게 한다.
- delivery·receipt 진단 기록은 delivery 생성 시각부터 30일로 결정했다. 알림 ID, 설치/연결 참조, 상태·시각·시도 횟수·정규화 오류·필요한 ticket ID만 보관하고 토큰/key/퀴즈 제목/본문/제공자 원문을 복제하지 않는다. 만료 배치 삭제를 검증하고 탈퇴 시 연결된 기록은 별도 법적 근거 없이 30일을 채우려고 유지하지 않는다. 기존 알림함은 90일, 활성 등록 자격은 별도 수명이다.
- 토큰/key는 접근을 제한하고 payload/header 로깅에서 제거한다. 개인 제목을 delivery 로그에 복제하지 않는다. 계정 탈퇴 시 연결과 발송 대상 제거는 필수이며 법적 보존 기간은 이 문서에서 새로 정하지 않는다.
- 문서 검증 대상: 정상 등록/동일 재시도, revision 충돌, 늦은 revoke, 두 기기, 재설치 동일 토큰, 계정 변경 중 401 refresh, cold-start 중 인증, ACK 유실, 삭제 대상/알림 자체 404, 읽음 저장 실패/재접속, 구앱·구웹 호환.

## 남은 제품·운영 결정

- 소급 제외와 결과 확정 후 1시간 발송은 확정이다. 등록·복구 시 과거 결과에 delivery를 추가하지 않는다.
- 재설치 복구·비활성 설치 30일·멱등 기록 7일 정책은 승인됐다. 요청 유효 시간과 파일별 구현 경계는 서버 TRD의 기술 설계·테스트로 구체화한다.
- PRD의 개인정보처리방침·국외 이전 고지 및 법적 근거 검토는 출시 전 충족해야 한다. OS 권한으로 대체하지 않는다.
- 초기 5초/50건, retry backoff·상한, receipt 조회 간격, 브리지 8KiB는 기술 기본값 제안이다. 실제 SDK·DB·단말 검증 후 TRD에서 조정한다.
- 읽음 큐의 계정 격리·재개 및 알림 자체 404 안내는 승인됐다. pending open의 구체 저장소·재전달 구현은 공유 ACK 조건을 유지해야 한다.

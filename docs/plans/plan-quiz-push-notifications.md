---
document_type: execution-plan
status: draft
scope: app-web-server
---

# 퀴즈 결과 푸시 기술 설계안

- 제품 기준: [OS 푸시 PRD](../prd/prd-quiz-push-notifications.md)
- 기존 경계: [알림 API](../contracts/contract-api-notifications.md), [알림 데이터](../contracts/contract-data-notifications.md), [앱 셸 TRD](../../app/docs/trd/trd-webview-shell.md)
- 공유 상세: [푸시 API·브리지 계약 초안](../contracts/contract-api-push-notifications.md)
- 상태: 2026-09-06 코드 확인을 바탕으로 한 기술 제안. 제품 합의와 기술 선택을 구분한다. 구현·외부 설정은 아직 하지 않았다.
- 책임: 이번 작업의 전체 접근과 연결 지점을 검토한다. HTTP·메시지·계정 경계의 상세 원장은 공유 Contract 초안이다. 앱별 지속 구현 결정은 해당 TRD에 반영한다.

## 1. 추천 구조

**확정(2026-09-06 사용자 합의)**: Expo Push Service를 공통 발송 경로로 사용한다. Spring 서버가 HTTPS로 요청하고 Expo가 iOS APNs와 Android FCM으로 전달한다. 별도 Node 서버는 필요하지 않다. 직접 APNs·FCM 연동은 세밀한 제어가 필요해질 때 비교한다. [Expo 발송 문서](https://docs.expo.dev/push-notifications/sending-notifications/)

```text
앱: OS 권한·기기 토큰 ── 제한된 메시지 브리지 ── 웹: 기존 로그인·기기 등록 API
                                                      │
웹 또는 앱에서 퀴즈 생성 → 서버: 결과 + 알림 + 기기별 발송 작업 저장
                                      │ 커밋 이후 별도 발송 작업자
                                      └→ Expo → APNs / FCM → 각 등록 기기
앱: 푸시 선택 → 웹 준비·인증 확인 → 알림 재조회 → 목적지 진입 → 읽음 동기화
```

| 계층 | 책임 | 기존 연결 지점 |
| --- | --- | --- |
| App | 권한·토큰, foreground 표시 억제, 알림 선택 보존·전달 | `app/App.tsx`, `app/src/shell/OpenMdWebView.tsx` |
| Web | 기존 인증으로 등록 API 호출, 알림별 라우팅, 읽음 재시도 | `authSession.ts`, `sessionCleanup.ts`, `notificationPresentation.ts`, `notificationStorage.ts` |
| Server | 사용자별 기기, 내구성 있는 발송 작업, 외부 오류·receipt 처리 | `QuizGenerationPersistenceService`, `NotificationService`, 인증 로그아웃·탈퇴 경로 |

Access Token은 웹 메모리, Refresh Cookie는 WebView cookie jar에 유지한다. 네이티브에 사용자 토큰을 전달하거나 별도 로그인·refresh를 만들지 않는다. 기존 단일 WebView와 React Router를 사용한다.

## 2. 앱 접속 확인: 두 수준을 구분

- **MVP 범위 확정(2026-09-06 사용자 합의)**: 일반 웹에서는 권한 요청·기기 등록을 실행하지 않고 앱에서 로그인한 경우에만 진행한다. 네이티브 셸과 웹의 버전 있는 handshake 성공 후 흐름을 실행하는 방식으로 설계한다. 토큰은 native API로 발급하고 웹의 기존 인증으로 서버에 등록한다.
- 서버는 인증 사용자, 설치 식별자, 등록 권한, 토큰 형식과 소유 관계를 검증한다. body의 `userId`, `isApp`, User-Agent를 권한 근거로 사용하지 않는다.
- **한계**: 브리지 존재·handshake·Expo 토큰 형식은 정식 앱 실행에 대한 암호학적 증명이 아니다. 인증된 사용자의 직접 API 호출까지 차단한다고 주장하지 않는다. 임의 토큰 탈취·재등록 공격을 막는 수준은 별도 설계가 필요하다.
- **후속 범위**: iOS App Attest, Android Play Integrity로 정식 앱의 증명을 서버에서 검증하는 것은 MVP에 포함하지 않는다. 도입 시 서버 challenge와 요청 내용을 연결하고 환경별 앱 ID·만료·재사용 및 지원하지 않는 기기 처리를 설계한다. [Apple](https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server), [Android](https://developer.android.com/google/play/integrity/overview)
- 이 합의는 일반 웹과 앱의 기능 진입 구분을 뜻한다. 사용자 인증·기기 소유권 검증을 생략하거나, 브리지 handshake로 위조 요청을 막는다고 간주하지 않는다.

## 3. 기기 등록과 계정 수명주기

1. 웹 인증 bootstrap 또는 로그인·가입 자동 로그인 완료 후 사용자 확인, 브리지 준비 완료를 기다린다. 기존 로그인 사용자의 앱 업데이트 후 첫 진입도 포함한다.
2. 네이티브가 권한 상태를 확인하고 미결정일 때 OS 팝업을 요청한다. 허용된 경우 project ID 기반 Expo 토큰을 발급한다. Android는 후속 지원 때 채널을 함께 설정한다.
3. 설치별 무작위 `installationId`와 `installationKey`를 네이티브 보안 저장소에 보관한다. 영구 하드웨어 ID는 사용하지 않는다. key·revision·binding의 의미와 재설치 토큰 충돌 처리는 공유 Contract를 따른다.
4. 웹은 기기 정보를 기존 인증 API 클라이언트로 등록한다. 사용자·세션은 서버 `AccessPrincipal(userId, sessionId)`에서 결정한다.
5. 앱 복귀·토큰 변경·같은 계정 재로그인 때 갱신한다. 다른 기기 행은 덮어쓰지 않는다. 계정 전환은 해당 설치의 이전 연결을 끊고 새 연결 revision을 만든다.
6. 명시적 로그아웃은 해당 설치 연결을 해제하고, 탈퇴는 전부 해제한다. 웹에서 단순 로그아웃했다고 다른 앱 기기를 일괄 해제하지 않는다.

`push_devices` 후보: `id`, `installationId`, `userId`, `sessionId`, `platform`, `provider`, `token`, `tokenVersion`, `bindingVersion`, `status`, `createdAt`, `updatedAt`. 동일 provider/token은 동시에 여러 계정에 활성 연결하지 않는다. 다른 계정 소유 행을 입력 ID만으로 탈취하지 못하도록 소유 증빙·계정 전환 절차를 Contract에 포함한다.

로그아웃 시 웹의 API 성공에만 기대지 않는다. 현재 `logoutCurrentSession`은 요청 실패에도 로컬 세션을 지우므로, native에 제한된 연결 해제 재시도 정보를 남기는 방안을 검토한다. 서버가 해제를 수신하기 전이나 이미 제공자에 넘긴 푸시까지 즉시 취소할 수는 없다. 세션 자연 만료는 명시적 로그아웃과 구분한다. 자연 만료만으로 기기를 지우면 합의한 '푸시 선택 후 재로그인' 흐름이 사라진다.

## 4. 공유 경계 상세 원장

| 후보 | 의미 |
| --- | --- |
| `PUT /api/v1/push-devices/{installationId}` | 현재 계정 기기 연결·토큰 갱신. 설치 증빙 및 예상 연결 revision 검증, 재요청 멱등 |
| `GET /api/v1/push-devices/{installationId}` | 설치 key를 검증한 뒤 현재 계정 연결 여부·revision 확인 |
| `POST /api/v1/push-devices/{installationId}/revoke` | 설치 key와 특정 binding으로 로그아웃 후에도 제한된 해제 재시도 |
| `GET /api/v1/notifications/{notificationId}` | 본인 소유·90일 보존 범위의 단일 알림 및 `targetAvailable` 조회 |
| 기존 `PUT /api/v1/notifications/{notificationId}/read` | 동일 알림의 최초 읽음만 반영하는 기존 멱등 API 재사용 |

목록 첫 페이지에 없는 오래된 푸시도 열 수 있어야 하므로 단일 조회를 추가한다. 없는 알림·타인 알림은 동일 404다. 알림은 있지만 대상이 삭제된 경우는 `targetAvailable=false`로 구분한다. 네트워크 오류·5xx는 삭제가 아니다.

브리지의 envelope·메시지 표와 ACK 종료 조건은 [공유 Contract](../contracts/contract-api-push-notifications.md#6-브리지-envelope와-연결)가 소유한다. 문서 세션과 인증 epoch를 별도로 검사한다. 특히 기존 `protectedApi`의 refresh·재전송 시 현재 토큰을 다시 읽는 경계에는 시작 계정을 고정해 검증하는 변경이 필요하다.

Expo data는 `payloadVersion`, `notificationId`, `bindingId`로 구성하고 표시 제목은 알림 snapshot을 사용한다. 이동 대상은 서버 단일 조회의 기존 `actionType`·대상 ID로 재구성한다. 상세 의미는 [공유 Contract](../contracts/contract-api-push-notifications.md#5-푸시-데이터)를 따른다.

## 5. 발송 내구성과 실패 처리

- **확정(2026-09-06 사용자 합의)**: 기존 서버의 주기적 스케줄러가 MySQL에 저장된 발송 대기·재시도 작업을 조회해 처리한다. 상시 조회 부하를 감수하고 서버 재시작 뒤에도 미완료 작업을 복구하는 방향을 선택했다.
- 퀴즈 결과·기존 알림 저장 트랜잭션에서 당시 활성 기기별 `push_deliveries` 행도 저장하는 DB outbox 방식으로 설계한다. 기존 성공·실패·stale/startup recovery의 모든 terminal 경로에 적용한다. 외부 HTTP는 이 트랜잭션 밖에서 실행한다.
- 별도 Kafka·Redis Queue 없이 기존 MySQL과 제한된 크기의 서버 발송 작업자를 사용한다. 사용자당 등록 기기가 MVP 규모를 크게 넘으면 fan-out을 별도 단계로 분리하는 설계를 다시 검토한다.
- **초기 설정 제안**: 5초 간격, 한 번에 최대 50건을 처리한다. `state + nextAttemptAt` 인덱스로 도래한 작업만 조회하고, 만료된 lease는 별도 인덱스로 소량 복구한다. 작업자의 빈 처리 용량만큼 claim하고 중첩 실행·무제한 메모리 큐를 막는다. 간격·건수는 설정값으로 두고 실제 조회 계획·처리 지연을 측정해 조정한다.
- Expo receipt 확인도 같은 스케줄러의 처리 대상에 포함하되 매 주기 외부 조회를 하지 않고 receipt 확인 시각이 된 작업만 처리한다. 발송·receipt 상태별 due 조회를 분리해 대기 작업을 반복 전송하지 않는다.
- 조회가 없을 때도 주기적 DB 접근은 발생한다. 별도 배포 서비스는 추가하지 않지만 실제 비용 영향은 DB 요금 방식과 부하에 달려 있으므로 무비용으로 간주하지 않는다.
- 작업 후보 필드: `notificationId`, `deviceId`, `bindingVersion`, `tokenVersion`, `state`, `attemptCount`, `nextAttemptAt`, `leaseUntil`, `ticketId`, `lastErrorCode`. `(notificationId, deviceId, bindingVersion)`은 unique다.
- `PENDING → SENDING(lease) → TICKET_ACCEPTED → PROVIDER_ACCEPTED`가 정상 흐름이다. 일시 실패는 `RETRY_WAIT`, 영구 실패는 `FAILED`, 연결 해제·대상 기간 초과는 `CANCELLED/EXPIRED`로 끝낸다. 공급자 접수를 실제 단말 수신이나 읽음으로 기록하지 않는다.
- 작업 claim과 결과 저장만 짧은 DB 트랜잭션으로 처리한다. HTTP 중 DB 잠금을 유지하지 않는다. lease 만료 작업은 복구하고, 시도 식별자로 늦은 응답이 새 시도를 덮어쓰지 못하게 한다.
- 발송 직전 사용자·기기 활성 상태와 연결 revision을 재확인한다. 계정이 바뀐 작업은 취소한다. 이전 토큰 receipt의 오류로 새 토큰을 비활성화하지 않도록 token version도 비교한다.
- 429·5xx·네트워크 오류는 상한 있는 지수 backoff와 jitter로 재시도한다. 잘못된 payload·인증 설정 오류는 무한 재시도하지 않는다. 발송 횟수와 간격은 기술 설정으로 구체화하되, 발송 기한은 합의된 결과 확정 후 1시간을 넘기지 않는다.
- `expiresAt`은 원래 알림 생성 시각을 기준으로 고정한다. claim 시와 외부 전송 직전에 만료를 확인하고 미접수 작업은 `EXPIRED`로 종료한다. Expo expiration도 같은 절대 시각으로 설정한다. ticket 접수 후 receipt 조회는 기한이 지나도 별도 일정에 따라 처리하며 만료된 알림을 재발송하지 않는다. 상세는 [발송 유효 시간 계약](../contracts/contract-api-push-notifications.md#발송-유효-시간)을 따른다.
- Expo ticket 후 receipt를 조회하고 `DeviceNotRegistered`는 해당 토큰을 비활성화한다. receipt는 제공자 인계 확인이며 단말 표시 증명이 아니다. [Expo 오류·receipt](https://docs.expo.dev/push-notifications/sending-notifications/)
- 서버가 제공자 접수 직후 죽거나 응답을 잃으면 재시도 중복이 가능하다. DB unique는 중복 작업 생성만 막으며 OS 배너 exactly-once를 보장하지 않는다. 앱의 중복 열기/읽음은 ID로 막고, OS 표시 중복은 별도 플랫폼 수단의 지원 범위 안에서 줄인다.
- 소급 발송하지 않는다. 기능 활성화 후 결과 확정 당시 활성인 기기 연결만 대상이며 신규 등록·재설치 복구로 과거 결과의 delivery를 추가하지 않는다. 발송 TTL은 결과 확정 후 1시간이다.
- 발송·receipt 최소 진단 기록은 delivery 생성 후 30일 보관한다. 기존 서버 스케줄러에서 만료 인덱스 기반 제한 배치로 정리하고 발송 작업과 트랜잭션을 분리한다. 백업 보존·복원 시 만료 데이터 재정리도 출시 전 검증한다. 활성 토큰·멱등 기록의 수명과 혼동하지 않는다.

## 6. foreground·알림 선택·읽음

- 서버에 '현재 사용 중' heartbeat를 유지하지 않는다. 서버는 기기별 발송을 수행하고 앱의 notification handler가 foreground에서 banner/list/sound/badge를 모두 끄는 방향을 제안한다. Android 진동까지 실제 기기에서 검증한다. handler는 WebView나 인증 완료를 기다리지 않고 앱 초기화 시 설치한다. [Expo SDK](https://docs.expo.dev/versions/latest/sdk/notifications/)
- background에서 이미 표시된 푸시를 앱 foreground 진입 시 소급 삭제하는 정책은 이번 합의에 포함하지 않는다. 표시 여부는 수신 당시 앱 상태 기준이다.
- 실행 중 응답 listener와 cold-start의 마지막 응답 조회를 모두 연결한다. native 로컬 저장소에 pending open을 먼저 보존하고 웹 처리 ACK 후 해제한다. SDK의 마지막 응답도 처리 후 정리해 다음 실행 때 재이동하지 않게 한다.
- listener는 WebView mount 전에 가능한 이른 앱 초기화 시점에 설치한다. 같은 `messageId`의 재전달은 새 화면 이동을 만들지 않는다. ACK는 목적지 진입(또는 대상 없음 복구)과 읽음 의도의 내구 저장이 완료됐을 때 보낸다. 서버 읽음 성공까지 native ACK를 미루지 않는다. 로그인 대기·네트워크 조회 실패에는 완료 ACK를 보내지 않는다.
- pending open이 기존 계정 연결에 속함을 확인할 수 있으면 그 계정 범위로 보존하고 다른 계정에서는 실행하지 않는다. 최종 서버 소유권 조회는 항상 필요하다. 단일 조회 404는 타인 소유와 만료를 구분할 수 없으므로 상세 이동·읽음 요청 없이 안전하게 종료하며, 이 경우의 pending 정리·안내는 공유 Contract에서 명시한다.
- WebView 준비 후 내부 notification 진입 route로 전달하고 로그인·소유권·대상 조회를 거친다. 기존 React Router에서 이동하며 정상 열기 때문에 WebView를 remount하지 않는다.
- 단순 `navigate` 호출 직후 읽지 않는다. 성공 퀴즈의 대상 카드 또는 실패 자료의 생성 조건이 확인된 뒤 읽음 의도를 저장한다. 삭제 대상은 안내·알림함 복구 완료 뒤 저장한다. 다른 계정·조회 실패 때는 읽음 의도를 만들지 않는다.
- 읽음 의도와 open 완료 기록은 웹 IndexedDB에 함께 내구 저장하는 방안으로 구체화했다. 필드·멱등키·재시도 조건은 [공유 Contract](../contracts/contract-api-push-notifications.md#8-읽음-재시도)를 따른다. 조회 캐시는 서버 성공 후 갱신하며 재시도 실패를 Snackbar로 알리지 않는다.
- 실패 시 foreground·online에서 제한적으로 재시도, 다음 bootstrap/복귀에서도 같은 사용자 큐만 처리한다. 401은 재인증까지 중단, 404는 알림 만료/삭제로 큐 제거, 429·5xx는 backoff한다. 계정이 바뀌면 실행 중 요청을 취소하고 다른 계정 큐를 실행하지 않는다.
- 로그아웃·다른 계정 사용 시 해당 계정 읽음 큐를 정지·격리하고 같은 계정 재접속 시 재개한다. 탈퇴·알림 보존 기간 종료 시 정리한다. '다음 앱 접속에도 동기화' 합의에 맞춰 기존 로그아웃 시 삭제 제안을 공유 Contract에서 보완했다. `sessionCleanup.ts`의 종료 이유를 구분해야 한다.
- 로컬 저장 실패를 메모리 성공으로 숨기지 않는다. 재시작 보존이 불가능한 상태는 진단에 남기고 화면은 유지한다. 앱 재설치·OS 데이터 삭제까지 큐 보존을 약속하지 않는다.

## 7. 기존 문서와 변경 경계

서버 파일 배치, 기존 코드 연결 지점, 트랜잭션·잠금·기술 기본값 및 테스트 파일은 [서버 푸시 TRD](../../server/docs/trd/trd-quiz-push-notifications.md)에 구체화한다. 서버 구현·실행 검증 현황은 아래 9절에 기록한다.

앱 셸 TRD의 '브리지·푸시 비범위'는 현재 버전 설명이다. 이 설계 채택 시 해당 절을 확장하고 허용 메시지를 공유 Contract에 연결한다. 기존 알림 API는 유지하면서 단일 조회와 기기 API를 추가한다. PRD의 중복 표시 방지 목표를 OS exactly-once로 확대하지 않는다.

발송 제공자는 Expo Push Service, MVP 앱 구분은 일반 웹에서 등록 흐름 제외로 확정됐다. 재설치 복구·404 안내·읽음 큐 정책과 비활성 설치 30일·멱등 기록 7일은 2026-09-06 승인됐다. 공유 계약에 오래된 변경 요청의 수명 검증안을 추가했고 서버 TRD에 파일·트랜잭션 설계를 정리했다. PRD의 개인정보·국외 이전 출시 요건은 별도 확인이 필요하다.

## 8. 구현 전 확인 및 검증 계획

- iOS: APNs 자격·push entitlement, EAS development/release 빌드, SDK 호환 패키지 설치가 필요하다. Android: package ID·FCM 자격·채널·빌드 구성을 후속 추가한다. 외부 자격 상태는 이번에 확인하지 않았다. [Expo 설정](https://docs.expo.dev/push-notifications/push-notifications-setup/)
- 서버 테스트: terminal 모든 경로의 작업 생성 원자성, 다기기 fan-out, worker 중단·lease 복구, 계정 전환 경합, ticket/receipt 오류, token version, 소유권과 단일 조회. 구현 때 저장소의 test-first 규칙 적용.
- 웹·앱 테스트: 웹 단독 미등록, 로그인 성공 후 권한 요청, 새 설치·토큰 갱신, cold/warm 진입, 다른 계정 404, 삭제 대상, durable read 재시도·계정 분리, 구버전 handshake 무응답.
- 실기기: 두 기기 중 하나 foreground/다른 하나 background, 종료 앱에서 알림 선택, 제목 표시, OS 권한 변경, 로그아웃 직전 발송, 네트워크 단절 후 재접속.
- 배포 순서 제안: additive 서버 기능(발송 off) → 구앱 호환 웹 → 푸시 앱 빌드 → 실제 생성 E2E → 신규 알림부터 발송 활성화. rollback은 발송 중단 후 기존 알림함 유지.
- 설계 단계에서는 문서 diff·참조를 확인했다. 이후 서버 실행 검증은 9절에 구분한다. 단말 도달·권한 팝업·foreground 무음 동작은 아직 미검증이다.

## 9. 서버 구현 진행 기록 (2026-09-06)

- 사용자 승인 범위: 서버만 구현하며 기기 관리 → Outbox·단일 조회 → 발송 Worker·정리 순서로 커밋을 분리한다. 웹·앱과 운영 발송 활성화는 제외한다.
- 변경 전 기준선: `cd server && ./gradlew fastTest --no-daemon` PASS (267 tests). Java 21 및 Docker 28.5.1 확인.
- 단일 알림 GET: 구현 전 MVC 테스트가 HTTP 404로 실패한 것을 확인한 후 라우트·서비스 구현. `./gradlew fastTest --tests '*NotificationControllerTest' --tests '*NotificationServiceTest' --no-daemon` PASS. 이후 응답 필드 검증 보강은 최종 회귀에서 재실행한다.
- 기기 관리·Expo 경계: 테스트 먼저 추가하고 미구현 타입에 대한 `compileTestJava` 실패 확인. 이는 동작 assertion 실패와 구분한다. 실제 MySQL/Redis 경합과 HTTP timeout 검증 결과는 실행 후 기록한다.
- 실제 Expo/APNs/FCM 호출과 단말 수신은 검증 대상에 포함하지 않는다. 가짜 제공자 테스트를 실제 발송 성공으로 보고하지 않는다.
- Outbox 연결 전 기존 결과 저장 테스트의 `saveAndFlush` 기대가 실제 `WantedButNotInvoked`로 실패함을 확인했다. 이후 다섯 terminal 경로를 공통 저장 helper로 연결했다.
- `./gradlew fastTest --tests '*push.*' --tests '*Notification*' --no-daemon` PASS (중간 검증; 최종 전체 회귀는 별도).
- `./gradlew integrationTest --tests '*PushOutboxIntegrationTest' --no-daemon` PASS (6 tests, MySQL 8.4): SQL CHECK로 outbox insert 실패 강제 후 QuizSet/notification/delivery 전체 rollback, 다기기·다섯 terminal 경로, 소급 제외, MANDATORY 경계, 단일 조회 소유권·90일을 확인했다.
- 전체 fast 회귀에서 DB auto-configuration을 제외한 기존 `ServerApplicationTests` 부팅 실패를 확인했다. 발송 비활성 Outbox의 저장소 주입을 지연해 기존 비활성 부팅 경계를 유지하고, `./gradlew fastTest --tests '*ServerApplicationTests' --tests '*PushOutboxServiceTest' --no-daemon` PASS를 확인했다. 활성 상태에서 저장소가 없으면 성공으로 숨기지 않는다.
- 기기 통합 검증 중 bulk delete의 persistence-context clear 때문에 같은 트랜잭션의 회원 탈퇴 상태가 저장되지 않는 실패를 확인하고 수정했다. 실제 logout/session 해제·탈퇴 정리를 포함한 MySQL/Redis 6개 검사가 통과했다.
- `lockedRegistrationRefreshesAnAlreadyManagedDeviceAfterConcurrentRevoke`: MySQL REPEATABLE READ와 JPA 1차 캐시를 먼저 만든 후 별도 트랜잭션에서 revoke를 커밋한다. 이전 revision 등록을 허용하는 RED를 확인했다. `refresh(..., PESSIMISTIC_WRITE)`만으로도 회귀가 남아 실제 SQL을 확인했으며, 이미 잠긴 엔티티의 refresh가 non-locking SELECT를 내는 것을 확인했다. 최초 조회한 기기 엔티티만 detach한 뒤 정렬된 locking query가 최신 행을 새로 적재하도록 수정했다. 전체 persistence context를 clear하지 않아 회원 등 다른 managed 객체는 보존한다.
- 전체 통합에서 기존 Notion의 고정 Clock과 충돌하는 부팅 실패를 확인했다. 새 전역 Clock 빈 대신 기존 Clock 또는 UTC fallback을 주입하며, `NotionInfrastructureIntegrationTest` 4개 PASS를 확인했다.
- 첫 커밋 `7cdf8c6`: staged 파일만 임시 체크아웃으로 분리해 `fastTest` 296개, `PushDeviceInfrastructureIntegrationTest` 7개 PASS. 늦은 해제 이후 이전 revision 등록 차단을 실제 MySQL에서 확인했다.
- 두 번째 커밋 대상도 staged 파일만 분리해 `fastTest` 304개, `PushOutboxIntegrationTest` 6개 PASS. 이후 worker가 추가되기 전 독립적으로 부팅·원자성·소유권을 검증했다.
- `PushDeliveryEndToEndIntegrationTest` 최초 2개 PASS: 실제 Spring proxy·JPA·MySQL·worker 연결에서 provider 대역 호출 시 TX가 없고 SENDING/RECEIPT_CHECKING이 이미 커밋돼 있음을 확인했다. 원래 제목 snapshot·정확한 1시간 expiresAt, ticket 이후 신규전송 없음·1시간 이후 receipt 조회·읽음 미변경을 확인했다. 토큰 갱신 후 이전 outbox 취소 시나리오 추가분은 최종 전체 검증에 포함한다.
- Expo adapter의 receipt HTTP 401을 `FAILED`로 해석하던 동작에 대해 `RETRY` 기대 RED를 확인했다. 조회 실패는 24시간까지 조회만 재시도하며, 실제 receipt 오류와 구분했다. `./gradlew fastTest --tests '*ExpoPushGatewayTest' --no-daemon` 12개 PASS: 절대 expiration/최소 data, 개별 오류, 429·Retry-After, malformed 응답, 연결 실패, 전체 응답 body deadline, receipt 누락 및 조회 거절을 제공자 대역으로 확인했다.

### 최종 서버 검증

`cd server && ./gradlew fastTest integrationTest bootJar --no-daemon` **PASS** (3분 39초).

| 대상 | 결과 |
| --- | --- |
| fastTest | 331개, 실패·오류·skip 0 |
| integrationTest | 70개, 실패·오류·skip 0 |
| bootJar | 실행 JAR 빌드 PASS |
| diff 검증 | `git diff --check`, staged diff check PASS |

푸시 통합 검사는 기기/Redis 8개, Outbox 6개, claim·retention 9개, 실제 서버 내부 연결 3개를 포함한다. 재활성화 경합 중 기기 보존, 같은 binding의 tokenVersion 변경, 이전 receipt 차단, 정리 후 오래된 PUT 차단, 탈퇴 사용자의 이관 전 delivery 삭제를 확인했다. 기존 Notion·인증·퀴즈 등 전체 서버 회귀도 포함했다.

기기 등록·delivery·scheduler flag는 모두 기본 false다. 웹·앱 코드는 변경하지 않았고 운영 발송 활성화·배포·실제 Expo/APNs/FCM 호출은 하지 않았다. 실기기 foreground 억제·cold start·푸시 선택은 후속 웹/앱 구현과 함께 검증해야 한다. FORCE INDEX EXPLAIN은 인덱스 존재·사용 가능성만 증명하며, 대표 운영 데이터의 자연 실행계획·조회 부하·백업 복원 후 보존 정리는 미검증이다.

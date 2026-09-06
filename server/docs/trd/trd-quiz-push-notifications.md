---
document_type: trd
status: draft
scope: server
---

# [TRD · Server] 퀴즈 결과 푸시 파일 구조와 트랜잭션

## 1. 범위와 상태

- 2026-09-06 현재 기기 등록·조회·해제, 설치 자격과 멱등 operation, Redis 제한, logout·탈퇴 연계(V12), delivery outbox(V13), send·receipt worker와 retention이 구현됐다. 실제 Expo 도달과 운영 부하 측정은 아직 검증하지 않았다.
- 제품 정책은 [PRD](../../../docs/prd/prd-quiz-push-notifications.md), 공개 API·브리지는 [공유 계약](../../../docs/contracts/contract-api-push-notifications.md), 통합 순서는 [실행 계획](../../../docs/plans/plan-quiz-push-notifications.md)이 소유한다.
- [패키지 규칙](trd-package-structure.md)을 따라 새 `com.openmd.server.push` 기능 도메인을 둔다. 기존 notification은 알림함, push는 기기와 외부 전달을 소유한다. 범용 메시징 프레임워크나 별도 서비스는 만들지 않는다.
- 출시 자격·개인정보 고지는 이번 서버 파일 설계에서 해결됐다고 간주하지 않는다.

## 2. 신규 파일 지도

아래 Java 경로는 `server/src/main/java/com/openmd/server/` 기준이다. DTO·enum은 실제 사용하는 타입만 생성한다.

| 파일/위치 | 책임 |
| --- | --- |
| `push/controller/PushDeviceController.java` | 등록·조회·제한 해제 HTTP 변환. 사용자/세션은 principal에서 추출 |
| `push/service/PushDeviceService.java` | 등록·해제 조정, 트랜잭션 종료 후 unique 충돌 분류·제한 재조회 |
| `push/service/PushDeviceTransaction.java` | 설치 자격, 요청 수명·멱등, revision, 계정 전환·토큰 이관의 원자 변경 |
| `push/service/PushOutboxService.java` | 이미 저장한 알림에서 당시 활성 연결별 작업 생성. 호출자 트랜잭션 필수 |
| `push/service/PushDeviceLifecycle.java`, `PushDeviceLifecycleService.java` | 인증 서비스의 session 해제·탈퇴 정리 연결 |
| `push/service/PushDeliveryWorker.java` | 작업 확보 → 외부 호출 → 결과 반영 조정. 자체 트랜잭션 없음 |
| `push/service/PushDeliveryTransaction.java` | claim, 전송 직전 재확인, 결과 조건부 반영, 만료 lease 복구. 메서드별 짧은 트랜잭션 |
| `push/service/PushDeliveryPolicy.java` | 제한 backoff·jitter, 발송 횟수·receipt 시각의 순수 계산 |
| `push/service/PushRetentionService.java` | 만료 delivery·operation·비활성 설치의 인덱스 기반 제한 삭제 |
| `push/service/PushGateway.java` | `sendBatch`·`getReceipts` 외부 경계. 테스트에서는 가짜 구현 사용 |
| `push/integration/expo/ExpoPushGateway.java` | HTTP 시간 제한, Expo 요청 변환, 응답 개수·형식 검사, 안정 오류로 정규화 |
| `push/domain/PushDevice.java` | 설치/현재 계정 연결, tokenVersion·revision·inactiveAt |
| `push/domain/PushDelivery.java` | 발송 상태, 원래 expiresAt, attemptId·lease, ticket·receipt 일정 |
| `push/domain/PushDeviceOperation.java` | operation별 요청 digest와 비밀 없는 응답 snapshot·삭제 시각 |
| `push/domain/PushDeliveryPolicy.java` | Clock에서 받은 시각과 시도 횟수로 만료·backoff 계산. 네트워크/DB 없음 |
| `push/domain/`의 상태 enum | 기기 상태·발송 상태·플랫폼·제공자. 초기에는 entity/type 하위 패키지를 강제하지 않음 |
| `push/repository/PushDeviceRepository.java` | 소유 연결 조회·잠금, 활성 token 유일성, session/user별 해제 |
| `push/repository/PushDeliveryRepository.java` | 작업 저장·조회·탈퇴 사용자별 삭제 |
| `push/repository/PushDeliveryClaimStore.java` | 복잡한 MySQL due/lease claim·fencing SQL 전용 저장소. 하나의 concrete 구현으로 시작 |
| `push/repository/PushRetentionStore.java` | delivery·operation·비활성 설치의 인덱스 기반 제한 삭제 SQL |
| `push/repository/PushDeviceOperationRepository.java` | 멱등 결과 조회·저장·탈퇴 사용자별 삭제 |
| `push/repository/PushRateLimitStore.java`, `push/repository/redis/RedisPushRateLimitStore.java` | 기존 Redis로 기기 변경 요청 빈도 제한. 발송 queue 용도로 Redis를 추가하는 것이 아님 |
| `push/security/PushInstallationCredential.java` | 설치 key digest·비교, 요청 내용 digest. 토큰/key 로깅 금지 |
| `push/error/PushErrorCode.java` | 공유 계약의 안정 오류 |
| `push/config/PushProperties.java`, `PushConfiguration.java` | 등록·delivery·scheduler 플래그와 batch/lease 설정 검증, HTTP 클라이언트 구성 |
| `push/config/PushSchedulingConfiguration.java`, `PushScheduler.java` | push 전용 scheduler와 기존 기본 scheduler 분리, send·receipt·lease·retention tick 조정 |
| `push/dto/request/`, `response/`, `model/` | 등록/해제 요청, 상태/결과 응답, claim snapshot·전송/receipt 결과 record |

외부 경계는 기존 `VerificationEmailSender`와 같은 방식으로 service에 인터페이스, integration에 구현을 둔다. 모든 Repository에 별도 port/adapter를 추가하지 않는다. 외부 호출에 JPA entity나 lazy relation을 넘기지 않고 immutable snapshot을 사용한다.

## 3. 기존 파일 수정 지점

| 기존 파일 | 최소 변경 |
| --- | --- |
| `quiz/service/QuizGenerationPersistenceService.java` | 다섯 terminal 알림 저장 지점을 private `saveTerminalNotificationAndOutbox(QuizSet)`로 통합하고 저장된 notification을 enqueue에 전달 |
| `notification/controller/NotificationController.java` | 인증된 단일 GET 추가 |
| `notification/service/NotificationService.java` | 기존 owned·90일·availability·item 변환을 재사용한 단일 조회 |
| `auth/service/AuthService.java` | 검증된 refresh session과 해당 설치 해제 연계. Redis와 MySQL의 원자성은 주장하지 않음 |
| `auth/service/RefreshTokenService.java` | 필요한 경우 검증된 revoke 결과에 userId/sessionId를 제공하는 좁은 메서드 추가. 임의 토큰 문자열에서 뽑은 sid만으로 해제 금지 |
| `auth/service/AccountWithdrawalService.java` | 기존 `withdrawInTransaction`에서 사용자 상태 변경과 전체 push 연결/관련 기록 정리를 함께 수행 |
| `auth/config/AuthConfiguration.java` | 수동 생성하는 auth/withdrawal 빈에 push 의존성 연결 |
| `auth/config/SecurityConfiguration.java` | POST revoke 경로만 제한 익명 허용, 설치 key header 및 필요한 Date 노출 CORS 검토. 나머지 기기 API 인증 유지 |
| `src/main/resources/application.properties` | 기본 비활성 발송 설정과 제한값. 자격 평문 없음 |
| `src/main/resources/db/migration/V<next>__create_push_notifications.sql` | 아래 세 테이블·인덱스. 현재 최신 V11이나 병합 직전 번호 재확인 |

`QuizGenerationWorker`, `QuizGenerationRecovery`, `QuizGenerationStartupRecovery`는 결과 저장 서비스 호출을 유지하며 각각 enqueue하지 않는다. 다섯 경로는 성공, 후보 부족 실패, 명시적 실패, 시작 시 중단 복구, stale 복구다.

## 4. 트랜잭션 경계

### 결과 저장

`QuizGenerationPersistenceService`의 기존 `@Transactional`과 QuizSet 소유 행 잠금을 유지한다. terminal이 이미 확정됐으면 기존처럼 아무 작업도 추가하지 않는다. private helper는 별도 트랜잭션을 열지 않는다.

1. QuizSet 상태/문항 저장.
2. 기존 알림 저장.
3. 별도 빈 `PushOutboxService.enqueue(notification)`를 `MANDATORY`로 호출.
4. 결과 확정 당시 활성 기기의 bindingId/tokenVersion snapshot별 delivery 저장.
5. 한 번에 커밋. delivery 저장 실패를 catch해서 알림만 커밋하지 않는다. `REQUIRES_NEW`·afterCommit 메모리 이벤트로 원자성을 끊지 않는다.

notification 생성 시각은 auditing 값이 확정된 후 읽는다. 구현 시작안은 notification `saveAndFlush` 뒤 실제 createdAt으로 expiresAt을 계산하는 것이다. flush는 commit이 아니므로 이후 outbox 실패 시 모두 rollback된다. flush 이전 timestamp 초기화 관례를 확인하지 않고 null이나 별도 현재 시각으로 만료를 계산하지 않는다.

발송 비활성일 때 enqueue는 no-op이고 기존 결과·알림은 정상 저장된다. 이후 활성화해도 기존 결과를 스캔해 보충하지 않는다. 회원탈퇴 경합은 발송 전 사용자 상태 확인과 탈퇴 정리로 차단하며 이미 외부로 넘긴 메시지는 회수 불가다.

### 발송/receipt 처리

```text
PushScheduler → PushDeliveryWorker (트랜잭션 없음)
  → PushDeliveryTransaction.claimSend (짧은 TX A)
  → PushDeliveryTransaction.prepareSend (짧은 TX B: 최신 연결/기한 재검사)
  → PushGateway.sendBatch (DB TX 없음)
  → PushDeliveryTransaction.recordSendResult (짧은 TX C)
```

- claim은 due 인덱스로 `FOR UPDATE SKIP LOCKED` 제한 조회 후 SENDING/attemptId/leaseUntil을 저장한다. 여러 작업자가 같은 시도를 소유하지 않게 한다. lease는 외부 호출 시간 제한보다 충분히 길어야 하며 대기 큐에서 만료되는 대량 선점은 금지한다.
- 결과 UPDATE 조건은 `id + expectedState + attemptId`다. 0행이면 오래된 응답으로 간주한다. 전달 오류로 기기를 끌 때도 bindingId/tokenVersion과 현재 토큰이 일치해야 한다.
- claim·prepare·결과 반영은 `PushDeliveryTransaction`의 서로 다른 짧은 트랜잭션이다. JDBC 시간은 DATETIME을 UTC로 해석하도록 epoch microsecond와 `TIMESTAMPADD`/`TIMESTAMPDIFF`를 사용하고 JPA JDBC timezone도 UTC로 고정한다.
- prepare의 join 잠금은 `FOR UPDATE OF d`로 delivery만 잠근다. 사용자·기기·알림 행까지 함께 잠가 등록/탈퇴의 user→device 순서와 역전하지 않는다. prepare 시점의 lease 만료와 현재 사용자·bindingId·tokenVersion을 다시 확인한다.
- receipt claim은 `TICKET_ACCEPTED` 중 due인 행만 별도의 attempt/lease로 확보한다. receipt 조회 실패를 send 재시도로 바꾸지 않는다. 접수 여부 미확정 작업과 이미 ticket을 가진 작업의 lease 복구 경로를 구분한다.
- 만료 lease는 기존 attempt를 무효화한다. ticket 없는 작업만 남은 발송 기한 내 재시도, ticket 있는 작업은 receipt 확인만 복구한다.
- 최초 포함 8회가 소진되면 `FAILED`, 원래 1시간 기한이 지났거나 다음 재시도가 기한 밖이면 `EXPIRED`다. claim되지 못한 만료·횟수 소진 행도 제한 배치로 terminal 상태로 닫는다.
- receipt 조회 HTTP 거절·일시 장애는 실제 단말 전달 실패가 아니므로 send 재시도로 바꾸지 않고 `TICKET_ACCEPTED`로 돌려 최대 접수 후 24시간까지만 다시 조회한다. 24시간 결과 미확정은 `UNKNOWN`, receipt가 반환한 확정 오류만 `FAILED`다.
- 1시간 제한은 notification.createdAt에서 고정한다. 서버 restart/attempt 변경으로 연장하지 않는다. DB unique는 중복 작업 생성만 막으며 Expo 접수 직후 crash의 중복 표시 가능성은 남는다.
- HTTP와 DB 잠금 사이 계정 변경의 아주 짧은 경합은 남는다. 전송 직전 재확인과 알림 선택 후 서버 소유권 확인을 적용하되 강제 회수를 보장하지 않는다.

Spring proxy를 거치는 별도 빈으로 나눠 self-invocation에 새 트랜잭션 경계를 의존하지 않는다. [Spring 트랜잭션](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html), [MySQL locking read](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)

### 기기 등록·해제와 잠금

- 설치 자격 검증, operation 유효성, 멱등 응답, revision 비교, 토큰 교체, operation 결과 저장을 같은 TX로 묶는다.
- 설치 ID unique와 활성 provider/token digest unique를 DB에 둔다. 없는 행은 단순 행 잠금만으로 보호된다고 가정하지 않고 unique 충돌 시 전체 TX rollback 후 제한 재조회한다.
- unique 충돌은 `PushDeviceTransaction` 프록시 밖의 `PushDeviceService`에서 분류한다. rollback-only가 된 트랜잭션 안에서 catch 후 계속 쓰지 않는다. 재조회로 동일 operation/같은 사용자 이관/타사용자 충돌을 구분하며 모든 unique 오류를 token conflict로 뭉개지 않는다.
- 토큰 이관에서 여러 설치 행을 잠그면 ID 정렬 순서로 잠근다. 멱등 결과가 있더라도 현재 인증·설치 자격 검증을 생략하지 않는다.
- 해제/갱신 트랜잭션은 delivery 전체를 잠가 일괄 수정하지 않는다. 연결 변경 후 worker가 snapshot mismatch를 취소하도록 해서 기기→delivery와 delivery→기기 잠금 역전을 피한다. 원자적 탈퇴 정리 경로는 같은 잠금 순서·deadlock 재시도 테스트를 포함한다.
- 명시적 logout은 Redis revoke와 MySQL 해제가 한 TX가 아니다. refresh token을 먼저 검증해 얻은 session 정보로만 MySQL 연결을 정리하고, Redis revoke 뒤 MySQL 정리가 실패하면 제한 재시도와 진단을 남기며 native pending revoke가 보완한다. 기존의 잘못된/만료된 refresh logout 멱등 동작은 유지한다. 성공 응답이 즉시 모든 OS 알림 회수를 뜻하지 않는다.
- 등록은 현재 사용자 행을 먼저 잠그고 ACTIVE·이메일 검증 상태를 트랜잭션 안에서 다시 확인한다. 이후 관련 설치 ID를 정렬해 잠그며, 탈퇴와 등록이 경합해 탈퇴 사용자에게 새 활성 토큰이 남지 않게 한다.
- 토큰 소유 설치를 찾는 일반 조회의 JPA 엔티티는 잠금 쿼리 전에 개별 detach한다. 잠금 쿼리 자체가 최신 행을 다시 적재하게 하여 REPEATABLE READ와 1차 캐시의 과거 revision을 사용하지 않는다. 전체 context clear는 같은 TX의 회원 변경을 누락시킬 수 있어 사용하지 않는다. 잠금 후 refresh만 호출하는 대안은 실제 Hibernate SQL이 non-locking SELECT여서 회귀 테스트를 통과하지 못했다.

## 5. 테이블과 인덱스 설계안

| 테이블 | 주요 내용·제약 |
| --- | --- |
| `push_devices` | installationId unique, keyDigest, userId/sessionId, bindingId, revision, tokenVersion, platform/provider, 발송용 token 및 tokenDigest, status, inactiveAt. 활성 tokenDigest만 non-null로 두어 provider+tokenDigest unique |
| `push_deliveries` | notificationId/deviceId/bindingId unique, tokenVersion, state, attemptId, attemptCount, expiresAt, nextAttemptAt, leaseUntil, ticketId, receiptNextAt, lastErrorCode, createdAt |
| `push_device_operations` | installationId+operationId unique, 요청 digest/주체/issuedAt, 비밀 없는 결과 snapshot, 최초 처리 시각/삭제 시각 |

- due 조회: `(state,nextAttemptAt,id)`, lease 복구: `(state,leaseUntil,id)`, receipt: `(state,receiptNextAt,id)`, 정리: createdAt/expireAt 및 `(status,inactiveAt,id)` 인덱스를 각 용도에 맞게 둔다. 통합 테스트의 `FORCE INDEX` EXPLAIN은 목적별 인덱스 존재와 쿼리 사용 가능성만 검증한다. 대표 운영 데이터에서 힌트 없는 자연 선택과 실제 부하는 아직 측정하지 않았다.
- delivery에는 raw token/제목/본문을 복제하지 않는다. 발송 준비에서 현재 유효 기기 토큰과 알림 snapshot을 조회한다. 삭제 대상의 FK 때문에 기록 정리가 막히지 않도록 migration에서 참조 정리 순서를 명시한다.
- 30일 delivery, 7일 operation, inactiveAt 기준 30일 설치는 서로 다른 삭제 기준이다. 토큰은 비활성 즉시 제거. 탈퇴 시 해당 사용자 기록을 운영 보존 기한까지 억지로 유지하지 않는다.
- 비활성 설치 삭제는 후보 ID뿐 아니라 외부 DELETE에서도 status와 inactiveAt cutoff를 다시 검사한다. 정리와 재등록이 경합해 먼저 커밋된 ACTIVE 연결을 삭제하지 않으며, 탈퇴는 기기가 다른 계정으로 이관됐어도 원래 userId의 delivery를 먼저 삭제한다.

## 6. 기술 기본값 제안

구현 테스트로 검증할 시작값이며 제품의 1시간 발송 기한은 변경하지 않는다.

| 항목 | 시작값 |
| --- | --- |
| 발송 활성화 | `delivery-enabled=false`, enqueue와 신규 전송 모두 차단; `scheduler-enabled=true`이면 정리·이미 접수된 receipt 확인은 계속 가능 |
| 발송 tick/배치 | fixed delay 5초 / 50개 이하. 한 배치를 한 HTTP 요청으로 처리하고 프로세스당 중첩 금지 |
| 기기 변경 API 빈도 | 초기 계정당 분당 60회 및 설치당 분당 20회, 익명 revoke는 설치당 20회. 초과 시 429/Retry-After. Redis 장애 시 변경은 503으로 보류하며 퀴즈 이용은 유지 |
| HTTP/lease | 연결 3초, 전체 호출 deadline 10초 / lease 60초. gateway 내부에 재시도 루프를 두지 않고 worker가 발송 시도 횟수 관리 |
| 발송 retry | 최초 포함 최대 8회, `min(30초 × 2^(실패횟수-1), 10분)`에 0~20% jitter. Retry-After가 더 길면 존중하되 expiresAt에서 종료 |
| receipt | ticket 접수 15분 뒤 첫 확인, 일시 실패/미준비는 5분 뒤 확인, 접수 후 24시간에 UNKNOWN으로 종료·재전송 금지 |
| 정리 | 1시간마다 각 종류 최대 500건, 적체는 다음 tick에서 처리. 실제 삭제 지연을 모니터링하고 백업 복원 후 재적용 |

외부 호출 전부터 전체 응답 처리까지 deadline을 제한한다. receipt 및 정리 때문에 기존 퀴즈 복구 scheduler가 지연되지 않도록 push 전용 실행 자원을 구성한다. UNKNOWN은 실패/단말 미수신을 확정한 상태가 아니라 제공자 결과를 더 확인할 수 없는 종료 상태다. [Expo 전송·receipt](https://docs.expo.dev/push-notifications/sending-notifications/)

`batch-size`는 1~50, retention batch는 1~500으로 검증하고 lease는 10초 provider deadline보다 길어야 한다. `pushTaskScheduler`를 1-thread 전용 scheduler로 명시하며, 기존 무지정 `@Scheduled` 작업용 `taskScheduler`와 분리한다. 모든 push flag의 기본값은 false다. 주기적 신규 send는 `scheduler-enabled=true`와 `delivery-enabled=true`가 모두 필요하다. `scheduler-enabled=true`이면 delivery가 false여도 기존 ticket의 Expo receipt 조회와 DB 정리는 계속되며, 이는 외부 호출이 전혀 없다는 의미의 OFF가 아니다. 전체 푸시 스케줄을 멈추려면 scheduler도 false로 둔다.

## 7. 테스트 파일과 구현 순서

아래 테스트를 구현했으며 선행 실패와 최종 실행 결과는 실행 계획에 구분해 기록한다.

1. `push/service/PushDeviceServiceTest`, `push/controller/PushDeviceControllerTest`: 소유권·revision·멱등·권한·계정 충돌·24시간 요청 만료.
2. `push/repository/PushDeviceInfrastructureIntegrationTest` (integration): V12 스키마와 비밀 미저장, 동일 설치 동시 등록, 동일 사용자 토큰 이관·타사용자 충돌, 실제 Redis 제한, logout·탈퇴 정리, 7/30일 정리 후 과거 PUT 재생 차단, 늦은 revoke와 이전 revision 재등록 차단.
3. 기존 `quiz/service/QuizGenerationNotificationTest` 확장 + `push/service/PushOutboxIntegrationTest`: 다섯 terminal 경로, 두 기기, 0기기, enqueue 실패 전체 rollback, 중복 완료, 발송 off/소급 제외.
4. `push/repository/PushDeliveryClaimStoreIntegrationTest` (integration): 두 worker의 비중복 claim, 제한 lease 복구, 이전 attempt UPDATE 0행, binding/tokenVersion fencing, 만료·8회·receipt 24시간 terminal 처리, retention과 인덱스 검증.
5. `push/service/PushDeliveryWorkerTest`, `push/service/PushDeliveryEndToEndIntegrationTest`, `push/integration/expo/ExpoPushGatewayTest`: HTTP 때 TX 미활성, JPA outbox에서 JDBC claim까지 UTC 경계, 오류/timeout, 응답 불일치, send/receipt 분리, 1시간 경계, tokenVersion.
6. 기존 `AuthServiceTest`, `AccountWithdrawalServiceTest`, `SecurityConfigurationTest`, `NotificationControllerTest`/`NotificationServiceTest`: logout 부분 실패, 탈퇴 rollback, 제한 revoke 경로, 단일 조회 소유권/90일.
7. `push/service/PushRetentionServiceTest` (unit): 삭제 cutoff·배치·호출 순서. 실제 삭제·재활성화 경합은 ClaimStore 통합, 탈퇴 기록 삭제·과거 요청 차단은 기기 통합 테스트가 담당한다.
8. `push/config/PushPropertiesTest`, `PushSchedulerTest`: 기본/경계 설정, 잘못된 설정 시작 실패, send OFF에서도 receipt 유지, push/default scheduler 분리.

집중 테스트 → `server/gradlew fastTest` → Docker 환경의 `server/gradlew integrationTest` 순으로 검증한다. 2026-09-06 단계 1 구현은 프로덕션 클래스가 없어서 발생한 compile 실패, V12 전의 Hibernate schema 실패, 보안/CORS 기대 실패를 각각 확인한 뒤 좁은 테스트를 통과시켰다. 최종 전체 검증 결과는 실행 계획에 기록한다. 테스트용 제공자 성공은 실제 푸시 도달 증거가 아니다.

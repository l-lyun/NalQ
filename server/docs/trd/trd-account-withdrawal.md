---
document_type: trd
status: implemented
scope: server
---

# [TRD · Server] 회원 탈퇴 확정과 세션 차단

- 소유 애플리케이션: `server/`
- 관련 PRD: [마이페이지와 계정 관리](../../../docs/prd/prd-mypage-account-management.md)
- 관련 Contract: [인증 API](../../../docs/contracts/contract-api-authentication.md), [사용자·인증 데이터](../../../docs/contracts/contract-data-authentication.md)

## 문서 책임

이 문서는 회원 탈퇴를 DB에서 확정하는 트랜잭션, 기존 Access Token 차단과 Redis Refresh 세션 정리 구조를 소유한다. 사용자에게 보이는 확인 흐름, 데이터별 후속 삭제 방식과 법률상 보존 범위는 상위 PRD와 공유 계약이 책임진다.

## 구현 구조

```text
UserController
  └─ AccountWithdrawalService
       ├─ TransactionOperations
       │    └─ UserRepository.findByIdForUpdate
       └─ RefreshTokenService.revokeAll
            └─ RefreshSessionStore

BearerAccessTokenFilter
  └─ UserRepository (ACTIVE 상태 확인)
```

- 컨트롤러는 인증된 `userId`와 검증된 요청 필드를 서비스에 전달하고, 성공할 때 브라우저 Refresh Cookie를 만료한다.
- 분리된 웹·API origin에서도 만료 `Set-Cookie`가 반영되도록 `/api/v1/users/me`는 정확한 일반·브라우저 origin에 한해 credentialed CORS를 허용한다. 인증 근거는 계속 Bearer Access Token이며 `Authorization`과 `Content-Type`만 허용한다.
- 서비스는 UUID와 정확한 `회원탈퇴` 문구를 먼저 검증한 뒤 사용자 행을 비관적 잠금으로 조회한다.
- 같은 요청 ID로 이미 확정된 탈퇴는 저장된 시각과 기한을 반환한다. 다른 요청 ID이거나 비활성 계정이면 `AUTH_005`로 처리한다.
- 최초 확정은 현재 비밀번호 확인, 식별·자격 필드 null 처리, `WITHDRAWN`, 탈퇴 시각, 요청 ID와 30일 처리 기한 저장을 한 DB 트랜잭션에서 수행한다.
- DB 커밋 뒤 사용자별 Redis session index로 전체 Refresh 세션을 폐기한다. 일시 실패는 한 번 즉시 재시도하며, 최종 실패해도 DB 상태 검사가 모든 보호 API와 refresh를 차단한다. 같은 요청 ID 재전송도 세션 정리를 다시 시도한다.
- 회원 탈퇴 endpoint만 서명·만료가 유효한 기존 Access Token의 subject를 허용해 동일 요청 결과를 재확인한다. 그 밖의 보호 API는 필터에서 `ACTIVE`와 이메일 인증 완료를 확인한다.

## 영속성과 Redis

- V9 migration은 탈퇴 계정에서 `email`, `normalized_email`, `password_hash`, `nickname`을 null로 만들 수 있게 하고 탈퇴 요청 ID, 처리 기한과 처리 완료 시각을 추가한다.
- `withdrawal_request_id`는 전역 unique이며, 상태 check는 탈퇴 tombstone의 직접 식별·자격 제거와 필수 처리 메타데이터를 강제한다.
- Refresh 세션 발급 시 `auth:user-sessions:{userId}` 의미의 사용자별 set에 session ID를 추가하고 가장 늦은 세션 절대 만료까지 TTL을 갱신한다. 전체 폐기는 set에 포함된 session key와 index를 삭제한다.
- 회전 tombstone과 로그아웃 뒤 남은 index member는 원래 TTL로 자연 만료하며 인증 자격으로 사용할 수 없다.

## 검증

- `AccountWithdrawalServiceTest`: 입력, 비밀번호, 원자 상태 전이, 멱등 결과와 Redis 재시도
- `UserControllerTest`, `OpenApiContractTest`: HTTP 응답, Cookie 만료와 공개 schema
- `BearerAccessTokenFilterTest`: 탈퇴 후 보호 API 차단과 동일 요청 재확인 예외
- `AuthenticationInfrastructureIntegrationTest`: MySQL V9 불변식·이메일/닉네임 재사용과 Redis 전체 세션 폐기
- 기본 회귀: `gradlew.bat fastTest`
- 운영 의존성: `gradlew.bat integrationTest`
- 전체 서버: `gradlew.bat test`

## 열린 질문

- 학습자료·퀴즈·풀이·복습 데이터 유형별 삭제 또는 비식별화 작업과 `withdrawal_disposal_completed_at` 기록은 공유 계약의 열린 정책이 확정된 후 별도 작업으로 구현한다.
- Redis 정리가 두 번 모두 실패했을 때의 영속 재시도 queue와 운영 경보는 첫 배포 운영 관측 설계에서 확정한다. 보안 접근 차단은 DB 상태 검사로 즉시 유지된다.

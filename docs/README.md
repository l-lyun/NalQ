---
document_type: index
status: draft
scope: repository
---

# [Index] OpenMD 문서 지도

이 파일은 OpenMD의 제품·UX·공유 계약·기술 원장을 찾기 위한 유일한 문서 인덱스다. 모든 문서를 한꺼번에 읽지 말고 현재 작업에 필요한 원장만 선택한다. 문서를 만들거나 분리할 때는 [문서 운영 가이드](guide.md)를 따른다.

## 읽기 순서

1. 제품의 목적, 범위, 원칙, 전역 내비게이션이나 용어를 판단할 때는 [제품 기반](product.md)을 읽는다.
2. 사용자 행동과 성공 조건을 만들거나 바꿀 때는 관련 [PRD](#제품-기반과-prd)를 읽는다.
3. 여러 화면의 분기·복구 또는 한 화면의 사용자 가시 상태가 중요할 때는 관련 [UX 문서](#ux)를 읽는다.
4. 웹·앱·서버가 공유하는 입출력이나 데이터 의미를 바꿀 때는 관련 [Contract](#공유-contract)를 읽는다.
5. 한 애플리케이션의 비자명한 구현 구조가 필요할 때만 해당 애플리케이션의 TRD를 읽는다.
6. 복잡한 현재 작업의 순서와 진행 상태는 필요한 경우 `docs/plans/`, 오래 남을 결정 이유는 필요한 경우 `docs/adr/`에 둔다. 두 폴더는 첫 실제 문서가 생길 때 만든다.

## 최소 문서 운영 방식

- 새 사용자 기능은 집중된 PRD 한 개로 시작한다.
- Flow, Screen Spec, Contract, Execution Plan, TRD, ADR은 각각의 복잡성이 실제로 있을 때만 추가한다.
- 한 정보에는 원장 하나만 두고 다른 문서는 해당 절을 링크한다.
- 확정, 제안, 열린 질문을 구분하며 미결정 사항을 구현 가정으로 승격하지 않는다.
- 문서 유형별 책임과 상태 의미는 [문서 운영 가이드](guide.md)를 기준으로 한다.

## 제품 기반과 PRD

| 관심사 | 원장 | 상태 |
| --- | --- | --- |
| 제품 목표·범위·원칙·내비게이션·용어 | [OpenMD 제품 기반](product.md) | 초안 |
| 홈 | [홈 PRD](prd/prd-home.md) | 초안 |
| 이메일 기반 자체 인증 | [자체 인증 PRD](prd/prd-local-authentication.md) | 초안 |
| 학습자료 만들기 | [학습자료 만들기 PRD](prd/prd-content-import.md) | 검토 중 |
| 퀴즈 생성·풀이·결과·복습 | [퀴즈 PRD](prd/prd-quiz-learning.md) | 검토 중 |

## UX

| 관심사 | 원장 | 유형 | 상태 |
| --- | --- | --- | --- |
| 이메일 기반 자체 인증 | [인증 흐름](ux/flow-authentication.md) | Flow | 초안 |
| 학습자료 만들기 | [학습자료 만들기 흐름](ux/flow-content-import.md) | Flow | 검토 중 |
| 퀴즈 생성부터 복습까지 | [퀴즈 흐름](ux/flow-quiz-solving.md) | Flow | 검토 중 |
| 홈 | [홈 화면](ux/screen-home.md) | Screen Spec | 초안 |
| 학습 | [학습 화면](ux/screen-learning.md) | Screen Spec | 초안 |
| 미완료 서술형 자기평가 재진입 | [미완료 서술형 자기평가 재진입 화면](ux/screen-quiz-resume.md) | Screen Spec | 초안 |
| 틀린 문제 다시 풀기 | [틀린 문제 다시 풀기 화면](ux/screen-review.md) | Screen Spec | 초안 |
| 프로필 | [프로필 화면](ux/screen-profile.md) | Screen Spec | 초안 |
| 회원가입 | [회원가입 화면](ux/screen-signup.md) | Screen Spec | 초안 |

Flow는 여러 화면의 순서와 분기를, Screen Spec은 한 화면의 구조와 사용자 가시 상태를 책임진다. 둘 다 PRD의 제품 정책을 복제하지 않는다.

## 공유 Contract

| 관심사 | 원장 | 유형 | 상태 |
| --- | --- | --- | --- |
| 이메일 기반 자체 인증 | [인증 API](contracts/contract-api-authentication.md) | API Contract | 초안 |
| 사용자와 인증 | [사용자·인증 데이터](contracts/contract-data-authentication.md) | Data Contract | 초안 |
| 학습자료·퀴즈·복습 | [학습·퀴즈 API](contracts/contract-api-quiz-learning.md) | API Contract | 검토 중 |
| 학습자료·퀴즈·복습 데이터 | [학습자료·퀴즈 데이터](contracts/contract-data-quiz-learning.md) | Data Contract | 검토 중 |

## 애플리케이션 TRD

TRD는 각 애플리케이션의 `docs/trd/trd-*.md`에 두며, 파일명과 제목에서 문서 유형과 소유 애플리케이션을 식별할 수 있어야 한다.

| 애플리케이션 | 관심사 | 원장 | 상태 |
| --- | --- | --- | --- |
| Server | 2단계 이메일 회원가입 | [서버 회원가입 TRD](../server/docs/trd/trd-two-step-signup.md) | 구현됨 |
| Server | 브라우저 Refresh Token Cookie | [서버 Cookie 전환 TRD](../server/docs/trd/trd-browser-refresh-cookie.md) | 구현 동기화, 웹 전환 완료 |
| Server | OpenAPI와 Swagger UI | [서버 OpenAPI 운영 TRD](../server/docs/trd/trd-openapi-documentation.md) | 초안 |
| Server | 패키지 구조 | [서버 패키지 구조 TRD](../server/docs/trd/trd-package-structure.md) | 구현 동기화 |
| Server | 학습자료 생성 | [학습자료 생성 TRD](../server/docs/trd/trd-learning-material-creation.md) | 검토 중 |
| Server | 퀴즈·복습 통합 저장 모델 | [퀴즈 채점 TRD](../server/docs/trd/trd-quiz-grading.md) | 목표 설계 확정, V5 SQL 반영·Java 구현 동기화 전 |
| Web | 인증 상태·토큰·API 통합 | [웹 인증 TRD](../web/docs/trd/trd-authentication.md) | 구현 동기화 |
| Web | 퀴즈 화면 상태 | [웹 퀴즈 TRD](../web/docs/trd/trd-quiz-solving.md) | 검토 중 |
| App | WebView 퀴즈 상태 | [앱 퀴즈 TRD](../app/docs/trd/trd-quiz-solving.md) | 검토 중 |

## 문서 템플릿

| 만들 문서 | 템플릿 |
| --- | --- |
| PRD | [PRD 템플릿](templates/template-prd.md) |
| Flow | [Flow 템플릿](templates/template-flow.md) |
| Screen Spec | [Screen Spec 템플릿](templates/template-screen-spec.md) |
| API Contract | [API Contract 템플릿](templates/template-api-contract.md) |
| Data Contract | [Data Contract 템플릿](templates/template-data-contract.md) |
| Execution Plan | [Execution Plan 템플릿](templates/template-execution-plan.md) |
| TRD | [TRD 템플릿](templates/template-trd.md) |
| ADR | [ADR 템플릿](templates/template-adr.md) |

작은 문구 수정, 명백한 버그 복원, 국소 리팩터링, 일회성 회의 메모에는 별도 제품 문서를 만들지 않는다.

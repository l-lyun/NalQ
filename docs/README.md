# Product Design Index

이 디렉터리는 OpenMD의 제품 설계 원장과 구현 계약을 찾기 위한 시작점이다. 모든 문서를 한꺼번에 읽지 말고 아래 표에서 현재 작업에 필요한 문서만 선택한다.

## 문서 운영 방식

문서를 새로 만들거나 분리할지 판단할 때는 [문서 운영 가이드](documentation-guide.md)를 확인한다. OpenMD의 기본은 사용자 기능당 하나의 집중된 PRD이며, 실제 복잡성이 있을 때만 UX 화면 명세·Flow·TRD 중 필요한 동반 문서를 추가한다. 둘 이상의 애플리케이션이 합의할 입력·출력은 Contract로 분리한다.

## 읽기 순서

1. 제품의 목적이나 범위를 판단할 때는 [제품 개요](product/overview.md)와 [제품 원칙](product/principles.md)을 읽는다.
2. 사용자에게 보이는 동작을 만들 때는 관련 [기능명세](features/)와 [사용자 흐름](flows/)을 읽는다.
3. 화면을 설계하거나 구현할 때는 관련 `docs/screens/` 문서를 읽는다.
4. 클라이언트와 서버의 경계를 바꿀 때는 [API 계약](contracts/api/)과 [데이터 계약](contracts/data/)을 읽는다.
5. 과거 판단의 이유가 필요할 때는 [결정 기록](decisions/)을 읽는다.

## 현재 원장

| 관심사 | 원장 | 상태 |
| --- | --- | --- |
| 제품 목표와 단계별 범위 | [overview.md](product/overview.md) | 초안 |
| 제품 판단 원칙 | [principles.md](product/principles.md) | 초안 |
| 하단 탭과 전역 이동 | [navigation.md](product/navigation.md) | 초안 |
| 공통 용어 | [glossary.md](product/glossary.md) | 초안 |
| 홈 | [00-home.md](features/00-home.md) | 초안 |
| 자체 로그인 | [01-local-auth.md](features/01-local-auth.md) | 초안 |
| 사용자·인증 데이터 | [authentication.md](contracts/data/authentication.md) | 초안 |
| 인증 API | [authentication.md](contracts/api/authentication.md) | 초안 |
| 학습자료 가져오기 | [02-content-import.md](features/02-content-import.md) | 초안 |
| 퀴즈 생성과 채점 | [03-quiz-generation.md](features/03-quiz-generation.md) | 초안 |
| 인증 흐름 | [authentication.md](flows/authentication.md) | 초안 |
| 자료 가져오기 흐름 | [content-import.md](flows/content-import.md) | 초안 |
| 문제 풀이 흐름 | [quiz-solving.md](flows/quiz-solving.md) | 초안 |
| 홈 화면 | [home.md](screens/home.md) | 초안 |
| 학습 화면 | [learning.md](screens/learning.md) | 초안 |
| 프로필 화면 | [profile.md](screens/profile.md) | 초안 |

화면 문서는 기능명세의 규칙을 복제하지 않고 관련 원장을 링크한다.

## 문서 생성 기준

- 새 사용자 가치를 정의할 때: [기능명세 템플릿](templates/feature-spec.md)
- 화면의 구조·상태·행동을 정의할 때: [화면 명세 템플릿](templates/screen-spec.md)
- 여러 상태와 분기를 정의할 때: [흐름 템플릿](templates/flow-spec.md)
- 클라이언트와 서버의 합의를 정의할 때: [API 계약 템플릿](templates/api-contract.md)
- 작은 문구 수정이나 구현 내부 결정은 별도 제품 문서를 만들지 않는다.

## 현재 열린 제품 질문

- 첫 배포 대상이 Expo 앱, 모바일 웹뷰, 웹 중 어디까지인지
- Refresh Token의 최종 수명과 웹/앱별 전달·보관 방식 (Access Token은 5분으로 확정)
- Notion 연결의 인증·페이지 선택 범위와 동기화 정책
- 난이도의 단계 수, 문제 수 선택 방식, 주관식 채점의 허용 기준
- 경험치·랭킹·친구·꾸미기 기능의 출시 순서와 운영 정책

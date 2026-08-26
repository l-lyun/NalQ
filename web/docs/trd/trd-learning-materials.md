---
document_type: trd
status: implemented
scope: web
---

# [TRD · Web] 학습자료 조회·생성 통합

- 소유 애플리케이션: `web/`
- 관련 PRD: [학습자료 만들기](../../../docs/prd/prd-content-import.md)
- 관련 UX: [학습 화면](../../../docs/ux/screen-learning.md)
- 관련 Contract: [학습자료·퀴즈·복습 API](../../../docs/contracts/contract-api-quiz-learning.md)

## 문서 책임

이 문서는 학습자료 목록·상세 Query와 생성 Mutation의 웹 내부 경계를 정의한다. 페이지 크기, 검색 의미, 공개 상태와 멱등 동작의 의미는 공유 API 계약이 책임진다.

## 기술 설계

- API 타입과 adapter는 `web/src/features/learning-material/api/`에 두며 서버 필드명을 화면 공개 모델에서도 유지한다. 목록 `Summary`, 페이지 `Page`, 상세 `Detail`을 분리하고 `updatedAtLabel`, `generating`, `selectable` 같은 중복 파생 필드를 공개 모델에 만들지 않는다.
- 모든 Query key는 로그아웃 시 함께 제거되는 `['private', 'learning-materials']` prefix를 사용한다. 목록 key에는 적용된 `page`, `size`, 정리한 `query`를 포함하고 상세 key에는 `materialId`를 포함한다.
- 목록은 페이지 이동·검색 재조회 중 TanStack Query의 이전 성공 데이터를 placeholder로 유지한다. 이때 화면은 갱신 중임을 텍스트로 알리고, 이전 페이지 항목을 새 결과로 오인해 선택하지 않도록 행과 페이지 이동을 잠시 비활성화한다.
- 현재 목록에 `LOCKED_GENERATING` 항목이 하나라도 있으면 3초마다 같은 목록 Query를 다시 조회한다. 최신 응답에서 잠긴 항목이 사라지면 polling을 즉시 중단한다.
- 검색어가 바뀌면 페이지를 1로 되돌린다. 화면 배열을 다시 필터링하지 않고 검색어를 서버 `query`로 전달한다.
- 상세 진입은 QueryClient의 상세 key로 조회한다. 이번 단계는 제목·본문·글자 수·출처·생성·수정 시각을 읽기 전용으로 표시하며 수정 상태나 PATCH adapter를 만들지 않는다.
- 학습자료 생성은 한 저장 작업의 정리된 제목, 원문 본문, 출처로 fingerprint를 만든다. 같은 payload의 실패 재시도에는 같은 UUID v4 `Idempotency-Key`를 유지하고, 입력이 달라지거나 성공한 뒤 시작한 저장에는 새 키를 만든다. 키와 fingerprint는 현재 화면 메모리에만 둔다.
- 생성 성공 뒤 학습자료 Query prefix를 무효화하고 응답의 `materialId`, `title`로 기존 문제 생성 조건 라우트에 진입한다. 생성 응답에 목록·상세 필드를 추측해 채우지 않는다.

## 오류와 상태

- 목록 최초 로딩, 이전 데이터가 있는 재조회, 전체 없음, 검색 결과 없음, 오류를 구분한다. 오류가 나도 이전 성공 데이터가 있으면 함께 유지한다.
- `contentEditStatus=LOCKED_GENERATING`은 학습 메인에서 임시 잠금 안내로, 기존 자료 선택에서는 생성 완료 전 선택 불가로 해석한다. 별도 잠금 상태를 로컬에서 추론하지 않는다.
- 상세 조회 실패는 자료가 삭제됐다고 단정하지 않고 같은 상세 Query를 다시 시도할 수 있게 한다.

## 검증

- `pnpm typecheck`
- `pnpm lint`
- `pnpm build`
- 목록 요청이 `page` 1-based, `size=6`, 서버 `query`를 사용하고 검색어 변경 시 첫 페이지로 돌아가는지 확인한다.
- 생성 실패 뒤 같은 입력 재시도와 입력 변경 뒤 재시도가 각각 같은 키와 새 키를 사용하는지 확인한다.

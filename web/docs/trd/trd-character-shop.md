---
document_type: trd
status: implemented
scope: web
---

# 웹 캐릭터 상점 통합

정책과 UI 기준: [PRD](../../../docs/prd/prd-character-shop.md). [API 계약](../../../docs/contracts/contract-api-character-shop.md)은 서버 원장이다.

- 마이페이지 방문 시 별도 lazy chunk로 실험 PNG와 정지 PixelSprite를 로드한다. 공개 랜딩과 학습의 초기 JS에 그림 자산을 eager import하지 않는다.
- `useCharacterShop`는 사용자 ID·auth epoch가 포함된 private query를 사용하고 기존 `protectedApi`에 요청을 시작한 authContext를 전달한다. logout의 기존 private query 제거와 호환된다.
- 상점/꾸미기 재진입과 브라우저 포커스 복귀 시 staleTime 0으로 최신 잔액·장착을 읽는다. 구매/장착 성공은 진행 중 조회를 취소한 다음 현재 계정의 캐시만 갱신한다.
- 구매는 서버에서 가격·보유·잔액을 판단한다. 클라이언트는 확인 후 itemId만 보내며 낙관적 차감을 하지 않는다. 실패 후 최신 정보를 재조회하며 같은 itemId 재시도는 서버 멱등성으로 안전하다.
- 장착 초안은 화면 컴포넌트 로컬 상태다. 서버 보유 목록과 슬롯 타입이 유효할 때만 저장하며 화면 이탈 시 버린다. 계정/모드별 key로 초안을 분리한다.
- 4개 카테고리는 현재 SEED ChipTabs, 작업은 ActionButton, 로딩은 Skeleton, 텍스트·레이아웃은 SEED primitive를 사용한다. PNG 합성 장면과 상품 비교 그리드는 기존 SEED 컴포넌트에 대응하지 않는 자산 표현이므로 좁은 커스텀 CSS를 사용한다. 장면에 조합 이름을 제공하고 장식 SVG는 접근성 트리에서 제외한다.
- 제품 장면은 정지 포즈여서 모션 감소 설정과 무관하게 자동 움직임이 없다. 기존 걷기 실험은 개발 경로에만 남긴다.
- `web/tests/characterShop.test.cjs`는 슬롯·보유 검증과 API 요청 의미, 계정 전환 전후 비동기 응답 차단을 검증한다. 브라우저 fixture 결과와 실제 서버 검증은 최종 PR에서 구분한다.

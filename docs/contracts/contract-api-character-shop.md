---
document_type: api-contract
status: approved
scope: web, server
---

# 캐릭터 상점 API

정책: [학습 보상 상점 PRD](../prd/prd-character-shop.md). 활성 로그인 사용자를 기존 Bearer 인증으로 식별한다. userId, 가격, 잔액을 클라이언트가 지정하지 않는다. 기존 `ApiResponse<T>` envelope를 사용한다.

## GET /api/v1/character-shop

계정별 상태와 서버 카탈로그를 함께 반환한다. 최초 조회는 0코인/기본 장착을 제공한다.

```json
{
  "balance": 0,
  "catalog": [
    {"id":"character-dragon","category":"CHARACTER","name":"꼬마 용","price":0,"assetKey":"dragon"},
    {"id":"character-cat","category":"CHARACTER","name":"고양이","price":50,"assetKey":"cat"},
    {"id":"character-bear","category":"CHARACTER","name":"곰","price":50,"assetKey":"bear"},
    {"id":"room-day","category":"ROOM","name":"낮 공부방","price":0,"assetKey":"day"},
    {"id":"room-night","category":"ROOM","name":"밤 공부방","price":30,"assetKey":"night"},
    {"id":"hat-none","category":"HAT","name":"모자 없음","price":0,"assetKey":"none"},
    {"id":"hat-beret","category":"HAT","name":"크림 베레모","price":20,"assetKey":"beret"},
    {"id":"hat-beanie","category":"HAT","name":"파란 비니","price":20,"assetKey":"beanie"},
    {"id":"top-none","category":"TOP","name":"기본 옷","price":0,"assetKey":"none"},
    {"id":"top-sweater","category":"TOP","name":"크림 니트","price":30,"assetKey":"sweater"}
  ],
  "ownedItemIds": ["character-dragon","room-day","hat-none","top-none"],
  "equipment": {"characterId":"character-dragon","roomId":"room-day","hatId":"hat-none","topId":"top-none"}
}
```

## POST /api/v1/character-shop/purchases

입력 `{"itemId":"hat-beret"}`. 성공 200, 위와 같은 최신 전체 상태 반환. 같은 계정·상품의 구매는 멱등하며 재전송에 추가 차감하지 않는다. 구매는 장착을 바꾸지 않는다. 잔액 확인, 차감, 보유 추가를 하나의 트랜잭션으로 직렬화한다.

## PUT /api/v1/character-shop/equipment

입력 `{"characterId":"character-dragon","roomId":"room-day","hatId":"hat-beret","topId":"top-none"}`. 네 슬롯 모두 필수. 해당 category의 보유 item ID만 허용. 한 트랜잭션으로 네 슬롯 교체 후 최신 전체 상태 반환. 다른 기기의 나중에 성공한 저장이 최종 상태다.

## 오류

- 기존 인증 401/403 정책 유지.
- 400 `CHARACTER_ITEM_INVALID`: 존재하지 않는 상품 또는 슬롯에 맞지 않는 상품.
- 409 `CHARACTER_INSUFFICIENT_COINS`: 구매에 필요한 잔액 부족.
- 409 `CHARACTER_ITEM_NOT_OWNED`: 미보유 아이템 장착.
- 필수 입력 누락/형식 오류는 기존 validation envelope의 400.
- 웹은 서버 거절이나 네트워크 오류에서 성공을 표시하지 않고 재조회·재시도를 제공한다. 응답이 유실된 구매의 재시도도 안전하다.

## 보상과 계정 수명주기

클라이언트용 코인 지급 API는 없다. 서버가 일반 퀴즈 완료 트랜잭션 안에서 최초 완료 여부를 확인하고 10코인을 지급한다. 사용자·퀴즈 세트당 고유 지급 기록과 계정 잠금으로 중복/동시 지급을 방지한다. 과거 완료가 있으면 지급하지 않는다. 복습은 제외한다. 사용자가 삭제한 학습 세트에 지급 기록의 FK cascade를 연결하지 않아 잔액은 보존한다. 계정탈퇴에서는 지갑·보유·장착·보상기록을 삭제한다.

## 클라이언트 동기화

개인 query key에 사용자 ID와 auth epoch를 포함하고 요청/응답의 기존 authContext 검증을 유지한다. 보호 화면 재진입 시 서버에서 잔액을 새로 읽는다. 로컬 storage를 계정 원장으로 사용하지 않는다. 구매/장착 성공 후 캐시 갱신으로 마이페이지에 반영한다.

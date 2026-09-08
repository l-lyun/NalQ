package com.openmd.server.character.error;

import com.openmd.server.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CharacterShopErrorCode implements ErrorCode {
  ITEM_INVALID(HttpStatus.BAD_REQUEST, "CHARACTER_ITEM_INVALID", "존재하지 않거나 슬롯에 맞지 않는 상품입니다."),
  INSUFFICIENT_COINS(HttpStatus.CONFLICT, "CHARACTER_INSUFFICIENT_COINS", "코인 잔액이 부족합니다."),
  ITEM_NOT_OWNED(HttpStatus.CONFLICT, "CHARACTER_ITEM_NOT_OWNED", "보유하지 않은 아이템입니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  CharacterShopErrorCode(HttpStatus status, String code, String message) {
    this.status = status;
    this.code = code;
    this.message = message;
  }

  @Override
  public HttpStatus status() {
    return status;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public String message() {
    return message;
  }
}

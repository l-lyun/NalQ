package com.openmd.server.character.domain;

import java.util.Arrays;
import java.util.Optional;

public enum CharacterCatalogItem {
  CHARACTER_DRAGON("character-dragon", CharacterItemCategory.CHARACTER, "꼬마 용", 0, "dragon"),
  CHARACTER_CAT("character-cat", CharacterItemCategory.CHARACTER, "고양이", 50, "cat"),
  CHARACTER_BEAR("character-bear", CharacterItemCategory.CHARACTER, "곰", 50, "bear"),
  ROOM_DAY("room-day", CharacterItemCategory.ROOM, "낮 공부방", 0, "day"),
  ROOM_NIGHT("room-night", CharacterItemCategory.ROOM, "밤 공부방", 30, "night"),
  HAT_NONE("hat-none", CharacterItemCategory.HAT, "모자 없음", 0, "none"),
  HAT_BERET("hat-beret", CharacterItemCategory.HAT, "크림 베레모", 20, "beret"),
  HAT_BEANIE("hat-beanie", CharacterItemCategory.HAT, "파란 비니", 20, "beanie"),
  TOP_NONE("top-none", CharacterItemCategory.TOP, "기본 옷", 0, "none"),
  TOP_SWEATER("top-sweater", CharacterItemCategory.TOP, "크림 니트", 30, "sweater");

  private final String id;
  private final CharacterItemCategory category;
  private final String displayName;
  private final int price;
  private final String assetKey;

  CharacterCatalogItem(
      String id, CharacterItemCategory category, String displayName, int price, String assetKey) {
    this.id = id;
    this.category = category;
    this.displayName = displayName;
    this.price = price;
    this.assetKey = assetKey;
  }

  public static Optional<CharacterCatalogItem> find(String id) {
    return Arrays.stream(values()).filter(item -> item.id.equals(id)).findFirst();
  }

  public String id() {
    return id;
  }

  public CharacterItemCategory category() {
    return category;
  }

  public String displayName() {
    return displayName;
  }

  public int price() {
    return price;
  }

  public String assetKey() {
    return assetKey;
  }

  public boolean free() {
    return price == 0;
  }
}

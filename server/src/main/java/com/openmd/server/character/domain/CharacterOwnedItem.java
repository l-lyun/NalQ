package com.openmd.server.character.domain;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "character_owned_items",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_character_owned_items_user_item", columnNames = {"user_id", "item_id"}))
public class CharacterOwnedItem extends BaseEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private long userId;

  @Column(name = "item_id", nullable = false, updatable = false, length = 32)
  private String itemId;

  protected CharacterOwnedItem() {}

  public static CharacterOwnedItem acquire(long userId, String itemId) {
    CharacterOwnedItem item = new CharacterOwnedItem();
    item.userId = userId;
    item.itemId = itemId;
    return item;
  }

  public long getUserId() {
    return userId;
  }

  public String getItemId() {
    return itemId;
  }
}

package com.openmd.server.character.domain;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "character_profiles",
    uniqueConstraints = @UniqueConstraint(name = "uk_character_profiles_user", columnNames = "user_id"))
public class CharacterProfile extends BaseEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private long userId;

  @Column(name = "coin_balance", nullable = false)
  private int balance;

  @Column(name = "character_item_id", nullable = false, length = 32)
  private String characterId;

  @Column(name = "room_item_id", nullable = false, length = 32)
  private String roomId;

  @Column(name = "hat_item_id", nullable = false, length = 32)
  private String hatId;

  @Column(name = "top_item_id", nullable = false, length = 32)
  private String topId;

  protected CharacterProfile() {}

  public static CharacterProfile initial(long userId) {
    CharacterProfile profile = new CharacterProfile();
    profile.userId = userId;
    profile.balance = 0;
    profile.characterId = CharacterCatalogItem.CHARACTER_DRAGON.id();
    profile.roomId = CharacterCatalogItem.ROOM_DAY.id();
    profile.hatId = CharacterCatalogItem.HAT_NONE.id();
    profile.topId = CharacterCatalogItem.TOP_NONE.id();
    return profile;
  }

  public void credit(int amount) {
    if (amount <= 0) throw new IllegalArgumentException("credit must be positive");
    balance = Math.addExact(balance, amount);
  }

  public boolean canSpend(int amount) {
    return amount >= 0 && balance >= amount;
  }

  public void spend(int amount) {
    if (!canSpend(amount)) throw new IllegalArgumentException("insufficient balance");
    balance -= amount;
  }

  public void equip(String characterId, String roomId, String hatId, String topId) {
    this.characterId = characterId;
    this.roomId = roomId;
    this.hatId = hatId;
    this.topId = topId;
  }

  public long getUserId() {
    return userId;
  }

  public int getBalance() {
    return balance;
  }

  public String getCharacterId() {
    return characterId;
  }

  public String getRoomId() {
    return roomId;
  }

  public String getHatId() {
    return hatId;
  }

  public String getTopId() {
    return topId;
  }
}

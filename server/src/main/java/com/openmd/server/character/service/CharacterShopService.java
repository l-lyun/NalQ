package com.openmd.server.character.service;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.character.domain.CharacterCatalogItem;
import com.openmd.server.character.domain.CharacterItemCategory;
import com.openmd.server.character.domain.CharacterOwnedItem;
import com.openmd.server.character.domain.CharacterProfile;
import com.openmd.server.character.dto.request.CharacterEquipmentRequest;
import com.openmd.server.character.dto.response.CharacterCatalogItemView;
import com.openmd.server.character.dto.response.CharacterEquipment;
import com.openmd.server.character.dto.response.CharacterShopState;
import com.openmd.server.character.error.CharacterShopErrorCode;
import com.openmd.server.character.repository.CharacterOwnedItemRepository;
import com.openmd.server.character.repository.CharacterProfileRepository;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CharacterShopService {

  private final UserRepository users;
  private final CharacterProfileRepository profiles;
  private final CharacterOwnedItemRepository ownedItems;

  public CharacterShopService(
      UserRepository users,
      CharacterProfileRepository profiles,
      CharacterOwnedItemRepository ownedItems) {
    this.users = users;
    this.profiles = profiles;
    this.ownedItems = ownedItems;
  }

  @Transactional
  public CharacterShopState get(long userId) {
    lockActiveUser(userId);
    return state(profile(userId), userId);
  }

  @Transactional
  public CharacterShopState purchase(long userId, String requestedItemId) {
    lockActiveUser(userId);
    CharacterCatalogItem item = item(requestedItemId);
    CharacterProfile profile = profile(userId);
    if (item.free() || ownedItems.existsByUserIdAndItemId(userId, item.id())) {
      return state(profile, userId);
    }
    if (!profile.canSpend(item.price())) {
      throw new BusinessException(CharacterShopErrorCode.INSUFFICIENT_COINS);
    }
    profile.spend(item.price());
    ownedItems.save(CharacterOwnedItem.acquire(userId, item.id()));
    return state(profile, userId);
  }

  @Transactional
  public CharacterShopState equip(long userId, CharacterEquipmentRequest request) {
    lockActiveUser(userId);
    CharacterCatalogItem character = slot(request.characterId(), CharacterItemCategory.CHARACTER);
    CharacterCatalogItem room = slot(request.roomId(), CharacterItemCategory.ROOM);
    CharacterCatalogItem hat = slot(request.hatId(), CharacterItemCategory.HAT);
    CharacterCatalogItem top = slot(request.topId(), CharacterItemCategory.TOP);
    requireOwned(userId, character);
    requireOwned(userId, room);
    requireOwned(userId, hat);
    requireOwned(userId, top);
    CharacterProfile profile = profile(userId);
    profile.equip(character.id(), room.id(), hat.id(), top.id());
    return state(profile, userId);
  }

  private User lockActiveUser(long userId) {
    User user = users.findByIdForUpdate(userId).orElseThrow(this::notFound);
    if (user.getStatus() != UserStatus.ACTIVE) throw notFound();
    return user;
  }

  private CharacterProfile profile(long userId) {
    return profiles
        .findByUserId(userId)
        .orElseGet(() -> profiles.save(CharacterProfile.initial(userId)));
  }

  private CharacterCatalogItem item(String itemId) {
    return CharacterCatalogItem.find(itemId)
        .orElseThrow(() -> new BusinessException(CharacterShopErrorCode.ITEM_INVALID));
  }

  private CharacterCatalogItem slot(String itemId, CharacterItemCategory category) {
    CharacterCatalogItem item = item(itemId);
    if (item.category() != category) {
      throw new BusinessException(CharacterShopErrorCode.ITEM_INVALID);
    }
    return item;
  }

  private void requireOwned(long userId, CharacterCatalogItem item) {
    if (!item.free() && !ownedItems.existsByUserIdAndItemId(userId, item.id())) {
      throw new BusinessException(CharacterShopErrorCode.ITEM_NOT_OWNED);
    }
  }

  private CharacterShopState state(CharacterProfile profile, long userId) {
    Set<String> purchased = new LinkedHashSet<>();
    ownedItems.findAllByUserIdOrderById(userId).forEach(item -> purchased.add(item.getItemId()));
    var catalog =
        Arrays.stream(CharacterCatalogItem.values())
            .map(
                item ->
                    new CharacterCatalogItemView(
                        item.id(),
                        item.category(),
                        item.displayName(),
                        item.price(),
                        item.assetKey()))
            .toList();
    var owned =
        Arrays.stream(CharacterCatalogItem.values())
            .filter(item -> item.free() || purchased.contains(item.id()))
            .map(CharacterCatalogItem::id)
            .toList();
    return new CharacterShopState(
        profile.getBalance(),
        catalog,
        owned,
        new CharacterEquipment(
            profile.getCharacterId(),
            profile.getRoomId(),
            profile.getHatId(),
            profile.getTopId()));
  }

  private BusinessException notFound() {
    return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
  }
}

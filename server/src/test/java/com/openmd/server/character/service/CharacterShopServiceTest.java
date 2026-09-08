package com.openmd.server.character.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.character.domain.CharacterOwnedItem;
import com.openmd.server.character.domain.CharacterProfile;
import com.openmd.server.character.dto.request.CharacterEquipmentRequest;
import com.openmd.server.character.dto.response.CharacterShopState;
import com.openmd.server.character.error.CharacterShopErrorCode;
import com.openmd.server.character.repository.CharacterOwnedItemRepository;
import com.openmd.server.character.repository.CharacterProfileRepository;
import com.openmd.server.global.error.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CharacterShopServiceTest {

  private final UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
  private final CharacterProfileRepository profiles =
      org.mockito.Mockito.mock(CharacterProfileRepository.class);
  private final CharacterOwnedItemRepository owned =
      org.mockito.Mockito.mock(CharacterOwnedItemRepository.class);
  private final CharacterShopService service = new CharacterShopService(users, profiles, owned);

  @BeforeEach
  void activeUser() {
    User user = org.mockito.Mockito.mock(User.class);
    when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
    when(profiles.save(any(CharacterProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(owned.findAllByUserIdOrderById(7L)).thenReturn(List.of());
  }

  @Test
  void initializesAZeroBalanceAccountWithTheFourFreeItemsEquipped() {
    when(profiles.findByUserId(7L)).thenReturn(Optional.empty());

    CharacterShopState state = service.get(7L);

    assertEquals(0, state.balance());
    assertEquals(10, state.catalog().size());
    assertEquals(
        List.of("character-dragon", "room-day", "hat-none", "top-none"),
        state.ownedItemIds());
    assertEquals("character-dragon", state.equipment().characterId());
    assertEquals("room-day", state.equipment().roomId());
    assertEquals("hat-none", state.equipment().hatId());
    assertEquals("top-none", state.equipment().topId());
  }

  @Test
  void purchasesOnceWithoutChangingEquipmentAndMakesRetryIdempotent() {
    CharacterProfile profile = CharacterProfile.initial(7L);
    profile.credit(50);
    when(profiles.findByUserId(7L)).thenReturn(Optional.of(profile));
    when(owned.existsByUserIdAndItemId(7L, "character-cat")).thenReturn(false, true);
    when(owned.findAllByUserIdOrderById(7L))
        .thenReturn(List.of(CharacterOwnedItem.acquire(7L, "character-cat")));

    CharacterShopState purchased = service.purchase(7L, "character-cat");
    CharacterShopState retried = service.purchase(7L, "character-cat");

    assertEquals(0, purchased.balance());
    assertEquals(0, retried.balance());
    assertEquals("character-dragon", purchased.equipment().characterId());
    verify(owned).save(any(CharacterOwnedItem.class));
  }

  @Test
  void rejectsAnInsufficientBalanceWithoutAddingOwnership() {
    CharacterProfile profile = CharacterProfile.initial(7L);
    when(profiles.findByUserId(7L)).thenReturn(Optional.of(profile));
    when(owned.existsByUserIdAndItemId(7L, "room-night")).thenReturn(false);

    BusinessException failure =
        assertThrows(BusinessException.class, () -> service.purchase(7L, "room-night"));

    assertEquals(CharacterShopErrorCode.INSUFFICIENT_COINS, failure.getErrorCode());
    assertEquals(0, profile.getBalance());
    verify(owned, never()).save(any());
  }

  @Test
  void equipsOnlyOwnedItemsInTheirMatchingSlots() {
    CharacterProfile profile = CharacterProfile.initial(7L);
    when(profiles.findByUserId(7L)).thenReturn(Optional.of(profile));
    when(owned.existsByUserIdAndItemId(7L, "hat-beret")).thenReturn(true);

    CharacterShopState state =
        service.equip(
            7L,
            new CharacterEquipmentRequest(
                "character-dragon", "room-day", "hat-beret", "top-none"));

    assertEquals("hat-beret", state.equipment().hatId());
  }

  @Test
  void rejectsAnItemInTheWrongSlotAndAnUnownedItem() {
    CharacterProfile profile = CharacterProfile.initial(7L);
    when(profiles.findByUserId(7L)).thenReturn(Optional.of(profile));

    BusinessException wrongSlot =
        assertThrows(
            BusinessException.class,
            () ->
                service.equip(
                    7L,
                    new CharacterEquipmentRequest(
                        "room-day", "room-day", "hat-none", "top-none")));
    BusinessException unowned =
        assertThrows(
            BusinessException.class,
            () ->
                service.equip(
                    7L,
                    new CharacterEquipmentRequest(
                        "character-cat", "room-day", "hat-none", "top-none")));

    assertEquals(CharacterShopErrorCode.ITEM_INVALID, wrongSlot.getErrorCode());
    assertEquals(CharacterShopErrorCode.ITEM_NOT_OWNED, unowned.getErrorCode());
    assertEquals("character-dragon", profile.getCharacterId());
  }
}

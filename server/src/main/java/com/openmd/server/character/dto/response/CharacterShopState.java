package com.openmd.server.character.dto.response;

import java.util.List;

public record CharacterShopState(
    int balance,
    List<CharacterCatalogItemView> catalog,
    List<String> ownedItemIds,
    CharacterEquipment equipment) {}

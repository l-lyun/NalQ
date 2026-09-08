package com.openmd.server.character.dto.response;

import com.openmd.server.character.domain.CharacterItemCategory;

public record CharacterCatalogItemView(
    String id,
    CharacterItemCategory category,
    String name,
    int price,
    String assetKey) {}

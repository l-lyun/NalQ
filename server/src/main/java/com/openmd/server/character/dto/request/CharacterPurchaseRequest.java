package com.openmd.server.character.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CharacterPurchaseRequest(@NotBlank @Size(max = 32) String itemId) {}

package com.openmd.server.character.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CharacterEquipmentRequest(
    @NotBlank @Size(max = 32) String characterId,
    @NotBlank @Size(max = 32) String roomId,
    @NotBlank @Size(max = 32) String hatId,
    @NotBlank @Size(max = 32) String topId) {}

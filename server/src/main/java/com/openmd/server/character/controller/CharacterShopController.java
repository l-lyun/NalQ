package com.openmd.server.character.controller;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.character.dto.request.CharacterEquipmentRequest;
import com.openmd.server.character.dto.request.CharacterPurchaseRequest;
import com.openmd.server.character.dto.response.CharacterShopState;
import com.openmd.server.character.service.CharacterShopService;
import com.openmd.server.global.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/character-shop")
public class CharacterShopController {
  private final CharacterShopService service;

  public CharacterShopController(CharacterShopService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<CharacterShopState>> get(
      @AuthenticationPrincipal AccessPrincipal principal) {
    return ResponseEntity.ok(ApiResponse.success(service.get(principal.userId())));
  }

  @PostMapping("/purchases")
  public ResponseEntity<ApiResponse<CharacterShopState>> purchase(
      @AuthenticationPrincipal AccessPrincipal principal,
      @Valid @RequestBody CharacterPurchaseRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(service.purchase(principal.userId(), request.itemId())));
  }

  @PutMapping("/equipment")
  public ResponseEntity<ApiResponse<CharacterShopState>> equip(
      @AuthenticationPrincipal AccessPrincipal principal,
      @Valid @RequestBody CharacterEquipmentRequest request) {
    return ResponseEntity.ok(ApiResponse.success(service.equip(principal.userId(), request)));
  }
}

package com.openmd.server.character.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.character.dto.response.CharacterEquipment;
import com.openmd.server.character.dto.response.CharacterShopState;
import com.openmd.server.character.service.CharacterShopService;
import com.openmd.server.global.error.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class CharacterShopControllerTest {

  private final CharacterShopService service = mock(CharacterShopService.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new CharacterShopController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(principalResolver())
            .build();
  }

  @Test
  void returnsTheAuthenticatedAccountsFullShopState() throws Exception {
    when(service.get(7L)).thenReturn(state());

    mockMvc
        .perform(get("/api/v1/character-shop"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.balance").value(0))
        .andExpect(jsonPath("$.data.ownedItemIds[0]").value("character-dragon"))
        .andExpect(jsonPath("$.data.equipment.roomId").value("room-day"));
  }

  @Test
  void purchasesAndEquipsUsingOnlyTheAuthenticatedUserId() throws Exception {
    when(service.purchase(7L, "hat-beret")).thenReturn(state());

    mockMvc
        .perform(
            post("/api/v1/character-shop/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":\"hat-beret\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            put("/api/v1/character-shop/equipment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"characterId\":\"character-dragon\",\"roomId\":\"room-day\","
                        + "\"hatId\":\"hat-none\",\"topId\":\"top-none\"}"))
        .andExpect(status().isOk());

    verify(service).purchase(7L, "hat-beret");
    verify(service)
        .equip(
            7L,
            new com.openmd.server.character.dto.request.CharacterEquipmentRequest(
                "character-dragon", "room-day", "hat-none", "top-none"));
  }

  private CharacterShopState state() {
    return new CharacterShopState(
        0,
        List.of(),
        List.of("character-dragon", "room-day", "hat-none", "top-none"),
        new CharacterEquipment("character-dragon", "room-day", "hat-none", "top-none"));
  }

  private HandlerMethodArgumentResolver principalResolver() {
    return new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == AccessPrincipal.class;
      }

      @Override
      public Object resolveArgument(
          MethodParameter parameter,
          ModelAndViewContainer mavContainer,
          NativeWebRequest webRequest,
          org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
        return new AccessPrincipal(7L, "session");
      }
    };
  }
}

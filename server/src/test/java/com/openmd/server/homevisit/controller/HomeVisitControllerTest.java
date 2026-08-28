package com.openmd.server.homevisit.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.homevisit.dto.response.HomeVisitSummary;
import com.openmd.server.homevisit.service.HomeVisitService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class HomeVisitControllerTest {
  private final HomeVisitService service = mock(HomeVisitService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new HomeVisitController(service))
          .setCustomArgumentResolvers(principal())
          .build();

  @Test
  void recordsTodayWithoutARequestBody() throws Exception {
    when(service.visit(7L)).thenReturn(new HomeVisitSummary(LocalDate.of(2026, 8, 28), 4));

    mvc.perform(post("/api/v1/home-visits"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.visitDate").value("2026-08-28"))
        .andExpect(jsonPath("$.data.consecutiveVisitDays").value(4));
    verify(service).visit(7L);
  }

  private HandlerMethodArgumentResolver principal() {
    return new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == AccessPrincipal.class;
      }

      @Override
      public Object resolveArgument(
          MethodParameter parameter,
          ModelAndViewContainer container,
          NativeWebRequest request,
          org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
        return new AccessPrincipal(7L, "session");
      }
    };
  }
}

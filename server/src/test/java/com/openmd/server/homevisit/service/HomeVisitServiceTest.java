package com.openmd.server.homevisit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.homevisit.repository.HomeVisitRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class HomeVisitServiceTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Test
  void recordsBySeoulDateAndCountsTheCurrentConsecutiveRun() {
    HomeVisitRepository visits = mock(HomeVisitRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-27T15:00:00Z"), SEOUL);
    HomeVisitService service = new HomeVisitService(visits, clock);
    LocalDate today = LocalDate.of(2026, 8, 28);
    when(visits.findVisitDatesOnOrBefore(7L, today))
        .thenReturn(
            List.of(
                today,
                today.minusDays(1),
                today.minusDays(2),
                today.minusDays(4)));

    var result = service.visit(7L);

    assertEquals(today, result.visitDate());
    assertEquals(3, result.consecutiveVisitDays());
    verify(visits).insertIfAbsent(7L, today, clock.instant());
  }

  @Test
  void sameUserAndDateIsIdempotent() {
    HomeVisitRepository visits = mock(HomeVisitRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-28T01:00:00Z"), SEOUL);
    HomeVisitService service = new HomeVisitService(visits, clock);
    LocalDate today = LocalDate.of(2026, 8, 28);
    when(visits.findVisitDatesOnOrBefore(7L, today)).thenReturn(List.of(today));

    var result = service.visit(7L);

    assertEquals(1, result.consecutiveVisitDays());
    verify(visits).insertIfAbsent(7L, today, clock.instant());
  }
}

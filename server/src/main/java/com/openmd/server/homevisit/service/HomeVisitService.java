package com.openmd.server.homevisit.service;

import com.openmd.server.homevisit.dto.response.HomeVisitSummary;
import com.openmd.server.homevisit.repository.HomeVisitRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.home-visit.enabled", havingValue = "true", matchIfMissing = true)
public class HomeVisitService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final HomeVisitRepository visits;
  private final Clock clock;

  @Autowired
  public HomeVisitService(HomeVisitRepository visits, ObjectProvider<Clock> clocks) {
    this(visits, clocks.getIfAvailable(Clock::systemUTC));
  }

  HomeVisitService(HomeVisitRepository visits, Clock clock) {
    this.visits = visits;
    this.clock = clock;
  }

  @Transactional
  public HomeVisitSummary visit(long userId) {
    LocalDate today = LocalDate.now(clock.withZone(SEOUL));
    visits.insertIfAbsent(userId, today, clock.instant());
    List<LocalDate> dates = visits.findVisitDatesOnOrBefore(userId, today);
    int consecutiveDays = 0;
    LocalDate expected = today;
    for (LocalDate date : dates) {
      if (!date.equals(expected)) break;
      consecutiveDays++;
      expected = expected.minusDays(1);
    }
    if (consecutiveDays == 0) {
      throw new IllegalStateException("Today's home visit was not persisted");
    }
    return new HomeVisitSummary(today, consecutiveDays);
  }
}

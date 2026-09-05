package com.openmd.server.homevisit.domain;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "home_visits")
public class HomeVisit extends BaseEntity {
  @Column(name = "user_id", nullable = false, updatable = false)
  private long userId;

  @Column(name = "visit_date", nullable = false, updatable = false)
  private LocalDate visitDate;

  protected HomeVisit() {}

  public static HomeVisit record(long userId, LocalDate visitDate) {
    if (userId < 1 || visitDate == null) throw new IllegalArgumentException();
    HomeVisit visit = new HomeVisit();
    visit.userId = userId;
    visit.visitDate = visitDate;
    return visit;
  }

  public long getUserId() {
    return userId;
  }

  public LocalDate getVisitDate() {
    return visitDate;
  }
}

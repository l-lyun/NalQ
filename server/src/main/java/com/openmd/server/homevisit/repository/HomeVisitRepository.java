package com.openmd.server.homevisit.repository;

import com.openmd.server.homevisit.domain.HomeVisit;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HomeVisitRepository extends JpaRepository<HomeVisit, Long> {
  @Modifying
  @Query(
      value =
          "INSERT INTO home_visits"
              + " (user_id, visit_date, created_at, updated_at)"
              + " VALUES (:userId, :visitDate, :now, :now)"
              + " ON DUPLICATE KEY UPDATE user_id = user_id",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("userId") long userId,
      @Param("visitDate") LocalDate visitDate,
      @Param("now") Instant now);

  @Query(
      "select visit.visitDate from HomeVisit visit"
          + " where visit.userId = :userId and visit.visitDate <= :date"
          + " order by visit.visitDate desc")
  List<LocalDate> findVisitDatesOnOrBefore(
      @Param("userId") long userId, @Param("date") LocalDate date);
}

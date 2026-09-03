package com.openmd.server.notification.repository;

import com.openmd.server.notification.domain.QuizGenerationNotification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository
    extends JpaRepository<QuizGenerationNotification, Long> {

  @Query(
      "select n from QuizGenerationNotification n where n.userId = :userId"
          + " and n.createdAt >= :retainedSince"
          + " and (:cursorCreatedAt is null or n.createdAt < :cursorCreatedAt"
          + " or (n.createdAt = :cursorCreatedAt and n.publicId < :cursorPublicId))"
          + " order by n.createdAt desc, n.publicId desc")
  List<QuizGenerationNotification> findPage(
      @Param("userId") long userId,
      @Param("retainedSince") Instant retainedSince,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorPublicId") String cursorPublicId,
      Pageable pageable);

  @Query(
      "select count(n) from QuizGenerationNotification n where n.userId = :userId"
          + " and n.readAt is null and n.createdAt >= :retainedSince")
  long countUnread(
      @Param("userId") long userId, @Param("retainedSince") Instant retainedSince);

  @Query(
      "select n from QuizGenerationNotification n where n.publicId = :publicId"
          + " and n.userId = :userId and n.createdAt >= :retainedSince")
  Optional<QuizGenerationNotification> findOwnedRetained(
      @Param("publicId") String publicId,
      @Param("userId") long userId,
      @Param("retainedSince") Instant retainedSince);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update QuizGenerationNotification n set n.readAt = :readAt"
          + " where n.userId = :userId and n.readAt is null"
          + " and n.createdAt >= :retainedSince"
          + " and (n.createdAt < :boundaryCreatedAt"
          + " or (n.createdAt = :boundaryCreatedAt and n.publicId <= :boundaryPublicId))")
  int markUnreadThrough(
      @Param("userId") long userId,
      @Param("retainedSince") Instant retainedSince,
      @Param("boundaryCreatedAt") Instant boundaryCreatedAt,
      @Param("boundaryPublicId") String boundaryPublicId,
      @Param("readAt") Instant readAt);
}

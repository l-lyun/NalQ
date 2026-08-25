package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.ReviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewSessionRepository extends JpaRepository<ReviewSession, Long> {
}

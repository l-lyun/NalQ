package com.openmd.server.quiz.dto.response;

import java.util.List;

public record QuizSetPage(
    List<QuizSetListItem> items,
    int page,
    int size,
    long totalElements,
    int totalPages) {}

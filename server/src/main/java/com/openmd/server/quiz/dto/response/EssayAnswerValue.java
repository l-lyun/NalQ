package com.openmd.server.quiz.dto.response;

import java.util.List;

public record EssayAnswerValue(String modelAnswer, List<String> keyPoints) {}

package com.openmd.server.quiz.domain;

public final class QuizTitlePolicy {
  public static final int MAX_CODE_POINTS = 255;
  private static final String SUFFIX = " 퀴즈";
  private static final int MATERIAL_TITLE_LIMIT =
      MAX_CODE_POINTS - SUFFIX.codePointCount(0, SUFFIX.length());

  private QuizTitlePolicy() {}

  public static String defaultTitle(String materialTitle) {
    String normalized = normalizeMaterialTitle(materialTitle);
    return truncate(normalized, MATERIAL_TITLE_LIMIT) + SUFFIX;
  }

  public static String normalize(String value) {
    String normalized = trimUnicodeWhitespace(value);
    int count = normalized.codePointCount(0, normalized.length());
    if (count < 1 || count > MAX_CODE_POINTS) {
      throw new IllegalArgumentException("quizTitle must contain 1 to 255 Unicode code points");
    }
    return normalized;
  }

  private static String normalizeMaterialTitle(String value) {
    String normalized = trimUnicodeWhitespace(value);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("materialTitle is required");
    }
    return normalized;
  }

  private static String truncate(String value, int limit) {
    if (value.codePointCount(0, value.length()) <= limit) return value;
    return value.substring(0, value.offsetByCodePoints(0, limit));
  }

  private static String trimUnicodeWhitespace(String value) {
    if (value == null || value.isEmpty()) return "";
    int start = 0;
    int end = value.length();
    while (start < end) {
      int codePoint = value.codePointAt(start);
      if (!isUnicodeWhitespace(codePoint)) break;
      start += Character.charCount(codePoint);
    }
    while (end > start) {
      int codePoint = value.codePointBefore(end);
      if (!isUnicodeWhitespace(codePoint)) break;
      end -= Character.charCount(codePoint);
    }
    return value.substring(start, end);
  }

  private static boolean isUnicodeWhitespace(int codePoint) {
    return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
  }
}

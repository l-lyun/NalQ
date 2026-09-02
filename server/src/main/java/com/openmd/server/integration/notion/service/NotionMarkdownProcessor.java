package com.openmd.server.integration.notion.service;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.integration.notion.client.NotionMarkdown;
import com.openmd.server.integration.notion.error.NotionErrorCode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NotionMarkdownProcessor {

	private static final Pattern MEDIA_TAG = Pattern.compile(
		"</?(?:audio|video|file|pdf)\\b[^>]*>", Pattern.CASE_INSENSITIVE
	);
	private static final Pattern UNKNOWN = Pattern.compile("<unknown(?:\\s[^>]*)?/?>", Pattern.CASE_INSENSITIVE);

	public String process(NotionMarkdown response) {
		if (response.truncated() || !response.unknownBlockIds().isEmpty()) {
			throw new BusinessException(NotionErrorCode.CONTENT_INCOMPLETE);
		}
		String markdown = response.markdown() == null ? "" : response.markdown();
		StringBuilder output = new StringBuilder(markdown.length());
		boolean fenced = false;
		int fenceDelimiterLength = 0;
		int inlineDelimiterLength = 0;
		for (int index = 0; index < markdown.length();) {
			char current = markdown.charAt(index);
			if (current == '`') {
				int runLength = backtickRunLength(markdown, index);
				boolean fenceMarker = inlineDelimiterLength == 0 && isFenceMarkerPosition(markdown, index)
					&& ((!fenced && runLength >= 3)
						|| (fenced && runLength >= fenceDelimiterLength
							&& isFenceClosingLine(markdown, index + runLength)));
				if (fenceMarker) {
					if (!fenced) {
						fenced = true;
						fenceDelimiterLength = runLength;
					} else {
						fenced = false;
						fenceDelimiterLength = 0;
					}
					output.append(markdown, index, index + runLength);
					index += runLength;
					continue;
				}
				if (fenced) {
					output.append(markdown, index, index + runLength);
					index += runLength;
					continue;
				}
				if (inlineDelimiterLength == 0) {
					inlineDelimiterLength = runLength;
				} else if (runLength == inlineDelimiterLength) {
					inlineDelimiterLength = 0;
				}
				output.append(markdown, index, index + runLength);
				index += runLength;
				continue;
			}
			if (!fenced && inlineDelimiterLength == 0) {
				Matcher unknown = UNKNOWN.matcher(markdown);
				unknown.region(index, markdown.length());
				if (unknown.lookingAt()) {
					throw new BusinessException(NotionErrorCode.CONTENT_INCOMPLETE);
				}
				if (markdown.startsWith("<br>", index)) {
					output.append("  \n");
					index += 4;
					continue;
				}
				Image image = imageAt(markdown, index);
				if (image != null) {
					output.append(image.altText());
					index = image.end();
					continue;
				}
				Matcher mediaTag = MEDIA_TAG.matcher(markdown);
				mediaTag.region(index, markdown.length());
				if (mediaTag.lookingAt()) {
					index = mediaTag.end();
					continue;
				}
			}
			output.append(current);
			index++;
		}
		return output.toString();
	}

	private static boolean isFenceMarkerPosition(String markdown, int index) {
		int lineStart = markdown.lastIndexOf('\n', index - 1) + 1;
		if (index - lineStart > 3) return false;
		for (int cursor = lineStart; cursor < index; cursor++) {
			if (markdown.charAt(cursor) != ' ') return false;
		}
		return true;
	}

	private static int backtickRunLength(String markdown, int start) {
		int end = start;
		while (end < markdown.length() && markdown.charAt(end) == '`') end++;
		return end - start;
	}

	private static boolean isFenceClosingLine(String markdown, int index) {
		for (int cursor = index; cursor < markdown.length() && markdown.charAt(cursor) != '\n'; cursor++) {
			char value = markdown.charAt(cursor);
			if (value != ' ' && value != '\t' && value != '\r') return false;
		}
		return true;
	}

	private static Image imageAt(String markdown, int start) {
		if (!markdown.startsWith("![", start)) return null;
		int altEnd = closingDelimiter(markdown, start + 2, ']', false);
		if (altEnd < 0 || altEnd + 1 >= markdown.length() || markdown.charAt(altEnd + 1) != '(') return null;
		int destinationEnd = closingDelimiter(markdown, altEnd + 2, ')', true);
		if (destinationEnd < 0) return null;
		return new Image(markdown.substring(start + 2, altEnd), destinationEnd + 1);
	}

	private static int closingDelimiter(String markdown, int start, char closing, boolean nestedParentheses) {
		int depth = 0;
		for (int index = start; index < markdown.length(); index++) {
			char value = markdown.charAt(index);
			if (value == '\n') return -1;
			if (value == '\\') {
				index++;
				continue;
			}
			if (nestedParentheses && value == '(') {
				depth++;
				continue;
			}
			if (value == closing) {
				if (depth == 0) return index;
				depth--;
			}
		}
		return -1;
	}

	private record Image(String altText, int end) { }
}

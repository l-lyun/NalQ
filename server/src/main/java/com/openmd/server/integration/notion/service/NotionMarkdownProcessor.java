package com.openmd.server.integration.notion.service;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.integration.notion.client.NotionMarkdown;
import com.openmd.server.integration.notion.error.NotionErrorCode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NotionMarkdownProcessor {

	private static final Pattern MEDIA = Pattern.compile("!\\[([^]]*)]\\([^\\n)]*\\)");
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
					&& ((!fenced && runLength >= 3) || (fenced && runLength >= fenceDelimiterLength));
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
				Matcher media = MEDIA.matcher(markdown);
				media.region(index, markdown.length());
				if (media.lookingAt()) {
					output.append(media.group(1));
					index = media.end();
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
}

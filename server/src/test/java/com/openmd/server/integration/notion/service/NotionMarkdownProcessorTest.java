package com.openmd.server.integration.notion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.integration.notion.client.NotionMarkdown;
import com.openmd.server.integration.notion.error.NotionErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotionMarkdownProcessorTest {

	private final NotionMarkdownProcessor processor = new NotionMarkdownProcessor();

	@Test
	void preservesCodeAndEnhancedMarkdownWhileConvertingOnlyTextBreaksAndRemovingMediaUrls() {
		String markdown = """
			# 제목
			첫 줄<br>둘째 줄
			`literal <br>`
			```html
			<div><br></div>
			```
			![그림 설명](https://signed.example/image.png)
			<callout>링크 [문서](https://example.com)</callout>
			""";

		assertEquals(
			"# 제목\n첫 줄  \n둘째 줄\n`literal <br>`\n```html\n<div><br></div>\n```\n"
				+ "그림 설명\n<callout>링크 [문서](https://example.com)</callout>\n",
			processor.process(new NotionMarkdown(markdown, false, List.of()))
		);
	}

	@Test
	void preservesBreaksInsideInlineCodeDelimitedByMatchingBacktickRuns() {
		String markdown = "일반<br>줄바꿈\n``literal <br> code``\n";

		assertEquals("일반  \n줄바꿈\n``literal <br> code``\n", processor.process(
			new NotionMarkdown(markdown, false, List.of())
		));
	}

	@Test
	void escapedOrUnclosedBackticksDoNotHideIncompleteMarkers() {
		for (String markdown : List.of(
			"literal \\` backtick <unknown value=\"missing\"/>",
			"unclosed ` code <unknown value=\"missing\"/>"
		)) {
			BusinessException exception = assertThrows(BusinessException.class,
				() -> processor.process(new NotionMarkdown(markdown, false, List.of())));
			assertEquals(NotionErrorCode.CONTENT_INCOMPLETE, exception.getErrorCode());
		}
	}

	@Test
	void rejectsIncompleteMarkersOutsideCodeWithoutReturningPartialContent() {
		for (NotionMarkdown markdown : List.of(
			new NotionMarkdown("partial", true, List.of()),
			new NotionMarkdown("partial", false, List.of("block")),
			new NotionMarkdown("before <unknown url=\"x\"/> after", false, List.of())
		)) {
			BusinessException exception = assertThrows(BusinessException.class, () -> processor.process(markdown));
			assertEquals(NotionErrorCode.CONTENT_INCOMPLETE, exception.getErrorCode());
		}

		assertEquals("`<unknown x=\"y\">`", processor.process(
			new NotionMarkdown("`<unknown x=\"y\">`", false, List.of())
		));
	}

	@Test
	void removesOfficialMediaTagsAndSignedUrlsButKeepsTheirCaptionTextOutsideCode() {
		String media = """
			<audio src="https://signed.example/audio">오디오 설명</audio>
			<video src="https://signed.example/video" poster="temporary">영상 설명</video>
			<file src="https://signed.example/file">파일 설명</file>
			<pdf src="https://signed.example/pdf">PDF 설명</pdf>
			`<audio src="literal">코드</audio>`
			""";

		assertEquals("""
			오디오 설명
			영상 설명
			파일 설명
			PDF 설명
			`<audio src="literal">코드</audio>`
			""", processor.process(new NotionMarkdown(media, false, List.of())));
	}

	@Test
	void removesImageUrlsWhenAltTextOrDestinationContainsMarkdownEscapes() {
		String markdown = "![설명 \\] 계속](https://signed.example/file\\))";

		assertEquals("설명 \\] 계속", processor.process(
			new NotionMarkdown(markdown, false, List.of())
		));
	}

	@Test
	void doesNotCloseAFenceWhenBackticksAreFollowedByNonWhitespace() {
		String markdown = "```text\n`````not-a-closing-fence\n<unknown value=\"literal\"/>\n```\n";

		assertEquals(markdown, processor.process(new NotionMarkdown(markdown, false, List.of())));
	}
}

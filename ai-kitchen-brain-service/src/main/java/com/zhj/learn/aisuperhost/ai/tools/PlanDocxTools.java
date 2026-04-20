package com.zhj.learn.aisuperhost.ai.tools;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;

@Component
@Slf4j
public class PlanDocxTools {

    @Value("${app.tools.docx.output-dir:./generated-docx}")
    private String outputDir;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Tool(description = "将结构化方案内容导出为docx文档，并返回绝对文件路径")
    public String generatePlanDocx(
            @ToolParam(description = "文档标题，例如：一周饮食计划") String title,
            @ToolParam(description = "方案正文，建议按换行组织多段内容") String planContent) {
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (!StringUtils.hasText(planContent)) {
            throw new IllegalArgumentException("planContent must not be blank");
        }

        try {
            Path dir = Paths.get(outputDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);

            String safeTitle = sanitizeFileName(title);
            String fileName = safeTitle + "_" + TIME_FORMATTER.format(LocalDateTime.now())
                    + "_" + UUID.randomUUID().toString().substring(0, 8) + ".docx";
            Path outputFile = dir.resolve(fileName);

            try (XWPFDocument document = new XWPFDocument();
                 OutputStream outputStream = Files.newOutputStream(outputFile, StandardOpenOption.CREATE_NEW)) {
                createTitle(document, title.trim());
                createBody(document, planContent);
                document.write(outputStream);
            }

            String absolutePath = outputFile.toString();
            log.info("Plan docx generated: {}", absolutePath);
            return absolutePath;
        } catch (IOException e) {
            log.error("Failed to generate plan docx", e);
            throw new IllegalStateException("Failed to generate docx: " + e.getMessage(), e);
        }
    }

    private void createTitle(XWPFDocument document, String title) {
        XWPFParagraph titleParagraph = document.createParagraph();
        titleParagraph.setStyle("Title");
        XWPFRun titleRun = titleParagraph.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(16);
        titleRun.setText(title);
    }

    private void createBody(XWPFDocument document, String content) {
        Arrays.stream(content.split("\\r?\\n"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(line -> {
                    XWPFParagraph bodyParagraph = document.createParagraph();
                    XWPFRun bodyRun = bodyParagraph.createRun();
                    bodyRun.setFontSize(12);
                    bodyRun.setText(line);
                });
    }

    private String sanitizeFileName(String rawTitle) {
        String cleaned = rawTitle.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.isEmpty() ? "plan" : cleaned;
    }
}

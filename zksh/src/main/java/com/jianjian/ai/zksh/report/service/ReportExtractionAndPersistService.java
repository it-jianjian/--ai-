package com.jianjian.ai.zksh.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.domain.dto.CreateNotificationDTO;
import com.jianjian.ai.zksh.domain.entity.HealthRecordEntity;
import com.jianjian.ai.zksh.mapper.HealthRecordMapper;
import com.jianjian.ai.zksh.report.ai.ReportClinicalSummaryAiService;
import com.jianjian.ai.zksh.report.ai.ReportMetricExtractionAiService;
import com.jianjian.ai.zksh.report.config.ReportProperties;
import com.jianjian.ai.zksh.report.domain.ReportTaskStage;
import com.jianjian.ai.zksh.report.domain.ReportTaskStatus;
import com.jianjian.ai.zksh.report.domain.entity.ReportInterpretationEntity;
import com.jianjian.ai.zksh.report.domain.entity.ReportOcrResultEntity;
import com.jianjian.ai.zksh.report.domain.entity.ReportTaskStepLogEntity;
import com.jianjian.ai.zksh.report.domain.model.ExtractedReport;
import com.jianjian.ai.zksh.report.mapper.ReportInterpretationMapper;
import com.jianjian.ai.zksh.report.mapper.ReportOcrResultMapper;
import com.jianjian.ai.zksh.report.mapper.ReportTaskMapper;
import com.jianjian.ai.zksh.report.mapper.ReportTaskStepLogMapper;
import com.jianjian.ai.zksh.service.NotificationService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReportExtractionAndPersistService {

    private final ReportTaskMapper reportTaskMapper;
    private final ReportOcrResultMapper reportOcrResultMapper;
    private final ReportInterpretationMapper reportInterpretationMapper;
    private final LocalReportFileStorage storage;
    private final AliyunGeneralOcrClient ocrClient;
    private final ReportMetricExtractionAiService extractionAiService;
    private final ReportClinicalSummaryAiService clinicalSummaryAiService;
    private final ObjectMapper objectMapper;
    private final HealthRecordMapper healthRecordMapper;
    private final NotificationService notificationService;
    private final ReportProperties reportProperties;
    private final ReportTaskStepLogMapper stepLogMapper;

    public ReportExtractionAndPersistService(ReportTaskMapper reportTaskMapper,
                                            ReportOcrResultMapper reportOcrResultMapper,
                                            ReportInterpretationMapper reportInterpretationMapper,
                                            LocalReportFileStorage storage,
                                            AliyunGeneralOcrClient ocrClient,
                                            ReportMetricExtractionAiService extractionAiService,
                                            ReportClinicalSummaryAiService clinicalSummaryAiService,
                                            ObjectMapper objectMapper,
                                            HealthRecordMapper healthRecordMapper,
                                            NotificationService notificationService,
                                            ReportProperties reportProperties,
                                            ReportTaskStepLogMapper stepLogMapper) {
        this.reportTaskMapper = reportTaskMapper;
        this.reportOcrResultMapper = reportOcrResultMapper;
        this.reportInterpretationMapper = reportInterpretationMapper;
        this.storage = storage;
        this.ocrClient = ocrClient;
        this.extractionAiService = extractionAiService;
        this.clinicalSummaryAiService = clinicalSummaryAiService;
        this.objectMapper = objectMapper;
        this.healthRecordMapper = healthRecordMapper;
        this.notificationService = notificationService;
        this.reportProperties = reportProperties;
        this.stepLogMapper = stepLogMapper;
    }

    @Transactional
    public void processTask(String taskId) {
        var task = reportTaskMapper.selectByTaskIdNoUser(taskId);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        if (ReportTaskStatus.SUCCEEDED.name().equals(task.getStatus())) {
            return;
        }

        String currentStepCode = "INIT";
        String currentStepName = "任务初始化";
        try {
            reportTaskMapper.updateStatusStage(taskId, ReportTaskStatus.RUNNING.name(), task.getStage(), null);
            logStep(taskId, "INIT", "任务初始化", "SUCCESS", "任务进入处理队列");

            // 1) OCR
            currentStepCode = "OCR";
            currentStepName = "OCR文字识别";
            logStep(taskId, currentStepCode, currentStepName, "RUNNING", "开始识别报告文本");
            byte[] bytes = storage.readAllBytes(task.getFilePath());
            String ocrText = extractOcrText(task.getFileName(), bytes);
            if (!StringUtils.hasText(ocrText)) {
                logStep(taskId, currentStepCode, currentStepName, "FAILED", "OCR 无结果");
                reportTaskMapper.updateStatusStage(taskId, ReportTaskStatus.FAILED.name(), task.getStage(), "OCR 无结果");
                return;
            }
            ReportOcrResultEntity ocrEntity = new ReportOcrResultEntity();
            ocrEntity.setTaskId(taskId);
            ocrEntity.setOcrText(ocrText);
            ocrEntity.setRawJson(ocrText);
            reportOcrResultMapper.insert(ocrEntity);
            reportTaskMapper.updateStatusStage(taskId, ReportTaskStatus.RUNNING.name(), ReportTaskStage.OCR_DONE.name(), null);
            logStep(taskId, currentStepCode, currentStepName, "SUCCESS", "OCR 完成，文本长度 " + ocrText.length());

            // 2) LLM 抽取 JSON
            currentStepCode = "EXTRACT";
            currentStepName = "结构化抽取";
            logStep(taskId, currentStepCode, currentStepName, "RUNNING", "开始提取检验指标");
            String json = extractionAiService.extract(compactOcrText(ocrText));
            ExtractedReport extracted = parseExtractedReport(json);
            if (extracted.getItems() == null) {
                extracted.setItems(List.of());
            }
            reportTaskMapper.updateStatusStage(taskId, ReportTaskStatus.RUNNING.name(), ReportTaskStage.INTERPRETED.name(), null);
            logStep(taskId, currentStepCode, currentStepName, "SUCCESS", "抽取指标 " + extracted.getItems().size() + " 项");

            // 3) 入库健康指标（MVP：直接写 health_record，多条）
            currentStepCode = "PERSIST";
            currentStepName = "健康指标入库";
            logStep(taskId, currentStepCode, currentStepName, "RUNNING", "开始写入健康档案");
            LocalDateTime recordTime = pickRecordTime(extracted);
            int persisted = 0;
            for (ExtractedReport.Item item : extracted.getItems()) {
                if (item == null) continue;
                if (!StringUtils.hasText(item.getCode()) || !StringUtils.hasText(item.getValue())) continue;
                double conf = item.getConfidence() == null ? 0.0 : item.getConfidence();
                if (conf < 0.5) continue;

                HealthRecordEntity hr = new HealthRecordEntity();
                hr.setUserId(task.getUserId());
                hr.setMetricType(toMetricType(item.getCode()));
                hr.setMetricValue(item.getValue());
                hr.setUnit(item.getUnit());
                hr.setRecordTime(recordTime);
                hr.setRemark(buildRemark(item));
                healthRecordMapper.insert(hr);
                persisted++;
            }
            reportTaskMapper.updateStatusStage(taskId, ReportTaskStatus.RUNNING.name(), ReportTaskStage.SAVED.name(), null);
            logStep(taskId, currentStepCode, currentStepName, "SUCCESS", "入库完成，写入 " + persisted + " 条");

            // 4) 告警通知（命中异常项时发）
            currentStepCode = "ALERT";
            currentStepName = "异常告警评估";
            logStep(taskId, currentStepCode, currentStepName, "RUNNING", "开始评估异常项");
            List<ExtractedReport.Item> abnormalItems = detectAbnormalItems(extracted.getItems());
            if (!abnormalItems.isEmpty()) {
                notificationService.create(task.getUserId(), new CreateNotificationDTO(
                        "检验指标异常提醒",
                        buildAlertContent(abnormalItems),
                        "health_alert",
                        LocalDateTime.now()
                ));
            }
            reportTaskMapper.updateStatusStage(taskId, ReportTaskStatus.RUNNING.name(), ReportTaskStage.ALERTED.name(), null);
            logStep(taskId, currentStepCode, currentStepName, "SUCCESS", "异常项 " + abnormalItems.size() + " 条");

            String summary = buildProfessionalSummary(extracted, abnormalItems);

            ReportInterpretationEntity interp = new ReportInterpretationEntity();
            interp.setTaskId(taskId);
            interp.setSummary(summary);
            interp.setStructuredJson(safeJson(json));
            reportInterpretationMapper.insert(interp);

            // 5) 完成通知
            currentStepCode = "NOTIFY";
            currentStepName = "结果通知";
            logStep(taskId, currentStepCode, currentStepName, "RUNNING", "发送处理完成通知");
            notificationService.create(task.getUserId(), new CreateNotificationDTO(
                    "报告解读完成",
                    summary == null ? "已完成报告识别与指标入库，可在健康档案中查看。" : summary,
                    "report",
                    LocalDateTime.now()
            ));
            reportTaskMapper.updateStatusStage(taskId, ReportTaskStatus.SUCCEEDED.name(), ReportTaskStage.NOTIFIED.name(), null);
            logStep(taskId, currentStepCode, currentStepName, "SUCCESS", "处理完成");
        } catch (Exception e) {
            String msg = e.getMessage();
            String safeMsg = (msg == null || msg.isBlank()) ? "报告处理失败" : msg;
            if (safeMsg.length() > 500) {
                safeMsg = safeMsg.substring(0, 500);
            }
            logStep(taskId, currentStepCode, currentStepName, "FAILED", safeMsg);
            reportTaskMapper.updateStatusStage(taskId, ReportTaskStatus.FAILED.name(), task.getStage(), safeMsg);
            throw e;
        }
    }

    private void logStep(String taskId, String code, String name, String status, String detail) {
        ReportTaskStepLogEntity log = new ReportTaskStepLogEntity();
        log.setTaskId(taskId);
        log.setStepCode(code);
        log.setStepName(name);
        log.setStatus(status);
        log.setDetail(detail == null ? null : (detail.length() > 500 ? detail.substring(0, 500) : detail));
        stepLogMapper.insert(log);
    }

    private String extractOcrText(String fileName, byte[] bytes) {
        if (isPdf(fileName)) {
            return extractPdfByRendering(bytes);
        }
        AliyunGeneralOcrClient.OcrResult ocr = ocrClient.recognize(bytes, detectImageMime(fileName)).block();
        return ocr == null ? null : ocr.text();
    }

    private boolean isPdf(String fileName) {
        return StringUtils.hasText(fileName) && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private String detectImageMime(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "image/jpeg";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private String extractPdfByRendering(byte[] bytes) {
        int maxPages = reportProperties.getPdf().getMaxPages() == null ? 3 : reportProperties.getPdf().getMaxPages();
        int dpi = reportProperties.getPdf().getRenderDpi() == null ? 180 : reportProperties.getPdf().getRenderDpi();

        StringBuilder merged = new StringBuilder();
        try (PDDocument doc = PDDocument.load(bytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int total = Math.min(doc.getNumberOfPages(), Math.max(1, maxPages));
            for (int i = 0; i < total; i++) {
                var image = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                byte[] imgBytes = toPngBytes(image);
                AliyunGeneralOcrClient.OcrResult pageResult = ocrClient.recognize(imgBytes, "image/png").block();
                if (pageResult != null && StringUtils.hasText(pageResult.text())) {
                    merged.append("## page ").append(i + 1).append("\n");
                    merged.append(pageResult.text()).append("\n");
                }
            }
            return merged.toString().trim();
        } catch (Exception e) {
            throw new BizException("PDF OCR 失败: " + e.getMessage());
        }
    }

    private byte[] toPngBytes(java.awt.image.BufferedImage image) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }

    private String compactOcrText(String text) {
        String t = text == null ? "" : text;
        if (t.length() <= 12000) return t;
        return t.substring(0, 12000);
    }

    private ExtractedReport parseExtractedReport(String json) {
        if (!StringUtils.hasText(json)) {
            throw new BizException("抽取失败：空响应");
        }
        try {
            return objectMapper.readValue(json, ExtractedReport.class);
        } catch (Exception e) {
            // 有些模型会在 JSON 前后夹杂少量文本，这里做一次兜底截取
            String trimmed = tryExtractJsonObject(json);
            try {
                return objectMapper.readValue(trimmed, ExtractedReport.class);
            } catch (Exception ex) {
                throw new BizException("抽取失败：JSON 解析失败");
            }
        }
    }

    private String tryExtractJsonObject(String s) {
        int l = s.indexOf('{');
        int r = s.lastIndexOf('}');
        if (l >= 0 && r > l) {
            return s.substring(l, r + 1);
        }
        return s;
    }

    private String safeJson(String s) {
        String trimmed = tryExtractJsonObject(s == null ? "" : s);
        return trimmed.length() > 20000 ? trimmed.substring(0, 20000) : trimmed;
    }

    private String buildSummary(ExtractedReport extracted) {
        if (extracted == null || extracted.getItems() == null) return "已完成报告识别与指标入库。";
        long count = extracted.getItems().stream().filter(x -> x != null && x.getConfidence() != null && x.getConfidence() >= 0.5).count();
        String date = extracted.getReportDate();
        if (StringUtils.hasText(date)) {
            return "已完成报告解读（" + date + "），抽取指标 " + count + " 项并已入库。";
        }
        return "已完成报告解读，抽取指标 " + count + " 项并已入库。";
    }

    private String buildProfessionalSummary(ExtractedReport extracted, List<ExtractedReport.Item> abnormalItems) {
        try {
            Map<String, Object> payload = Map.of(
                    "reportType", extracted == null ? "unknown" : (extracted.getReportType() == null ? "unknown" : extracted.getReportType()),
                    "reportDate", extracted == null ? null : extracted.getReportDate(),
                    "abnormalItems", formatAbnormalItems(abnormalItems),
                    "itemCount", extracted == null || extracted.getItems() == null ? 0 : extracted.getItems().size(),
                    "notes", extracted == null ? null : extracted.getNotes()
            );
            String aiText = clinicalSummaryAiService.summarize(objectMapper.writeValueAsString(payload));
            String clean = sanitizeSummary(aiText);
            if (StringUtils.hasText(clean)) {
                return clean;
            }
        } catch (Exception ignored) {
            // 模型不可用时走规则回退
        }
        return fallbackProfessionalSummary(extracted, abnormalItems);
    }

    private List<Map<String, Object>> formatAbnormalItems(List<ExtractedReport.Item> abnormalItems) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (abnormalItems == null) return result;
        int limit = Math.min(8, abnormalItems.size());
        for (int i = 0; i < limit; i++) {
            ExtractedReport.Item item = abnormalItems.get(i);
            if (item == null) continue;
            result.add(Map.of(
                    "code", defaultText(item.getCode(), "unknown"),
                    "name", defaultText(item.getName(), "未知指标"),
                    "value", defaultText(item.getValue(), "-"),
                    "unit", defaultText(item.getUnit(), ""),
                    "refRange", defaultText(item.getRefRange(), "-"),
                    "flag", defaultText(item.getFlag(), "U")
            ));
        }
        return result;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String sanitizeSummary(String text) {
        if (!StringUtils.hasText(text)) return null;
        String clean = text.trim();
        if (clean.length() > 900) {
            clean = clean.substring(0, 900);
        }
        return clean;
    }

    private String fallbackProfessionalSummary(ExtractedReport extracted, List<ExtractedReport.Item> abnormalItems) {
        if (abnormalItems == null || abnormalItems.isEmpty()) {
            return "异常指标：本次主要指标未见明显异常。\n可能原因：当前结果整体稳定，但仍需结合近期作息与既往病史综合判断。\n可能相关病症：暂未提示明确异常相关风险。\n建议：保持规律作息与清淡饮食，按医嘱定期复查。\n本解读仅供参考，不能替代医生面对面诊疗。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("异常指标：");
        int limit = Math.min(4, abnormalItems.size());
        for (int i = 0; i < limit; i++) {
            ExtractedReport.Item item = abnormalItems.get(i);
            if (i > 0) sb.append("、");
            String name = StringUtils.hasText(item.getName()) ? item.getName() : defaultText(item.getCode(), "未知指标");
            sb.append(name).append(defaultDirection(item));
        }
        sb.append("。\n可能原因：可能与近期饮食结构、代谢状态、慢性炎症或药物影响有关，需结合临床表现判断。");
        sb.append("\n可能相关病症：可能提示糖脂代谢异常、肝功能负担增加或电解质紊乱风险，不能作为确诊依据。");
        sb.append("\n建议：建议尽快携带报告就诊内科/相关专科，必要时空腹复查生化及电解质，并按医生建议开展进一步检查。");
        sb.append("\n本解读仅供参考，不能替代医生面对面诊疗。");
        return sb.toString();
    }

    private String defaultDirection(ExtractedReport.Item item) {
        if (item == null || !StringUtils.hasText(item.getFlag())) return "异常";
        String f = item.getFlag().trim().toUpperCase(Locale.ROOT);
        if ("H".equals(f)) return "偏高";
        if ("L".equals(f)) return "偏低";
        return "异常";
    }

    private LocalDateTime pickRecordTime(ExtractedReport extracted) {
        if (extracted != null && StringUtils.hasText(extracted.getReportDate())) {
            try {
                LocalDate d = LocalDate.parse(extracted.getReportDate(), DateTimeFormatter.ISO_LOCAL_DATE);
                return d.atStartOfDay();
            } catch (Exception ignored) {
            }
        }
        return LocalDateTime.now();
    }

    private String toMetricType(String code) {
        String c = code.trim().toLowerCase().replaceAll("\\s+", "_");
        // lab_ 前缀避免和现有 vital/lifestyle 冲突
        return "lab_" + c;
    }

    private String buildRemark(ExtractedReport.Item item) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(item.getName())) sb.append(item.getName()).append(" ");
        if (StringUtils.hasText(item.getRefRange())) sb.append("参考:").append(item.getRefRange()).append(" ");
        if (StringUtils.hasText(item.getFlag())) sb.append("标记:").append(item.getFlag()).append(" ");
        if (StringUtils.hasText(item.getEvidence())) {
            String ev = item.getEvidence();
            sb.append("证据:").append(ev.length() > 120 ? ev.substring(0, 120) : ev);
        }
        String s = sb.toString().trim();
        return s.isBlank() ? null : s;
    }

    private List<ExtractedReport.Item> detectAbnormalItems(List<ExtractedReport.Item> items) {
        List<ExtractedReport.Item> abnormal = new ArrayList<>();
        if (items == null) return abnormal;
        for (ExtractedReport.Item item : items) {
            if (item == null) continue;
            if (isFlagAbnormal(item.getFlag()) || isOutOfRefRange(item.getValue(), item.getRefRange())) {
                abnormal.add(item);
            }
        }
        return abnormal;
    }

    private boolean isFlagAbnormal(String flag) {
        if (!StringUtils.hasText(flag)) return false;
        String f = flag.trim().toUpperCase(Locale.ROOT);
        return "H".equals(f) || "L".equals(f);
    }

    private boolean isOutOfRefRange(String value, String refRange) {
        Double v = parseNumber(value);
        if (v == null || !StringUtils.hasText(refRange)) return false;
        Matcher m = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*[-~]\\s*(-?\\d+(?:\\.\\d+)?)").matcher(refRange);
        if (!m.find()) return false;
        try {
            double low = Double.parseDouble(m.group(1));
            double high = Double.parseDouble(m.group(2));
            return v < low || v > high;
        } catch (Exception e) {
            return false;
        }
    }

    private Double parseNumber(String text) {
        if (!StringUtils.hasText(text)) return null;
        Matcher m = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(text);
        if (!m.find()) return null;
        try {
            return Double.parseDouble(m.group());
        } catch (Exception e) {
            return null;
        }
    }

    private String buildAlertContent(List<ExtractedReport.Item> items) {
        StringBuilder sb = new StringBuilder("发现异常指标：");
        int limit = Math.min(items.size(), 5);
        for (int i = 0; i < limit; i++) {
            ExtractedReport.Item item = items.get(i);
            String code = StringUtils.hasText(item.getCode()) ? item.getCode() : "unknown";
            String value = StringUtils.hasText(item.getValue()) ? item.getValue() : "-";
            String unit = StringUtils.hasText(item.getUnit()) ? item.getUnit() : "";
            String flag = StringUtils.hasText(item.getFlag()) ? item.getFlag() : "";
            if (i > 0) sb.append("；");
            sb.append(code).append("=").append(value).append(unit);
            if (!flag.isBlank()) sb.append("(").append(flag).append(")");
        }
        if (items.size() > limit) {
            sb.append("；其余 ").append(items.size() - limit).append(" 项请在健康档案查看");
        }
        String content = sb.toString();
        return content.length() > 480 ? content.substring(0, 480) : content;
    }
}


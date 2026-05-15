package com.jianjian.ai.zksh.rag.service;

import com.jianjian.ai.zksh.common.BizException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文本抽取服务（V1）。
 *
 * <p>职责：把上传文件转换为纯文本，供后续切片/向量化使用。</p>
 * <ul>
 *   <li>txt/md：按 UTF-8 读取</li>
 *   <li>pdf：使用 PDFBox 提取文本</li>
 * </ul>
 *
 * <p>说明：V1 不做 OCR；如果 PDF 是扫描件，会抽不出文字，需要后续引入 OCR 才能覆盖。</p>
 */
@Service
public class TextExtractService {

    public String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try {
            if (filename.endsWith(".pdf")) {
                return extractPdf(file);
            }
            // txt / md / others treat as text
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException("读取文件失败: " + e.getMessage());
        }
    }

    private String extractPdf(MultipartFile file) {
        try (InputStream in = file.getInputStream(); PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (Exception e) {
            throw new BizException("解析 PDF 失败: " + e.getMessage());
        }
    }
}


package com.jianjian.ai.zksh.report.service;

import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.report.config.ReportProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class LocalReportFileStorage {

    private final ReportProperties props;

    public LocalReportFileStorage(ReportProperties props) {
        this.props = props;
    }

    public String save(Long userId, String taskId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (StringUtils.hasText(original) && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String safeExt = ext.length() > 16 ? "" : ext;
        String name = UUID.randomUUID().toString().replace("-", "") + safeExt;

        Path baseDir = Path.of(props.getStorageDir()).toAbsolutePath().normalize();
        Path dir = baseDir.resolve(String.valueOf(userId)).resolve(taskId);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(name);
            file.transferTo(target.toFile());
            return target.toAbsolutePath().normalize().toString();
        } catch (IOException e) {
            throw new BizException("保存文件失败: " + e.getMessage());
        }
    }

    public byte[] readAllBytes(String path) {
        if (!StringUtils.hasText(path)) {
            throw new BizException("文件路径为空");
        }
        File f = new File(path);
        if (!f.exists() || !f.isFile()) {
            throw new BizException("文件不存在");
        }
        try {
            return Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            throw new BizException("读取文件失败: " + e.getMessage());
        }
    }
}


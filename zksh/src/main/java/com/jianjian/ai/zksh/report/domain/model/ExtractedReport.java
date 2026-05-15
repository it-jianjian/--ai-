package com.jianjian.ai.zksh.report.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedReport {
    private String reportType;
    private String reportDate;
    private Patient patient;
    private List<Item> items;
    private String notes;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Patient {
        private String name;
        private String sex;
        private String age;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String code;
        private String name;
        private String value;
        private String unit;
        private String refRange;
        private String flag;
        private Double confidence;
        private String evidence;
    }
}


package com.aiacademy.academic;

public enum RiskLevel {
    LOW("Thấp"),
    MEDIUM("Trung bình"),
    HIGH("Cao"),
    CRITICAL("Rất cao");

    private final String label;

    RiskLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

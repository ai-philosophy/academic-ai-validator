package com.aiacademy.academic;

public enum ChecklistPriority {
    HIGH("Cao"),
    MEDIUM("Trung bình"),
    LOW("Thấp");

    private final String label;

    ChecklistPriority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

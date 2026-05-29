package com.aiacademy.academic;

public enum Verdict {
    LOW_RISK_TEXTUAL("Ít dấu hiệu rủi ro trong văn bản"),
    MISSING_CITATION("Thiếu bằng chứng học thuật"),
    NEEDS_REVIEW("Cần xem xét lại"),
    NEEDS_SOURCE_CHECK("Cần kiểm tra nguồn/citation"),
    OVERSTATED("Lập luận quá mạnh so với bằng chứng"),
    POSSIBLE_HALLUCINATION("Có nguy cơ AI bịa hoặc gán sai nguồn");

    private final String label;

    Verdict(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

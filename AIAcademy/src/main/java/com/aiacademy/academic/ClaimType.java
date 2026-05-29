package com.aiacademy.academic;

public enum ClaimType {
    CONCEPT_DEFINITION("Khái niệm/định nghĩa"),
    AUTHORSHIP_OR_SOURCE("Tác giả/công trình"),
    STATISTIC_OR_DATA("Số liệu/thống kê"),
    DATE_OR_HISTORY("Mốc thời gian/lịch sử"),
    CITATION("Trích dẫn"),
    CAUSAL_ARGUMENT("Lập luận nhân quả"),
    GENERALIZATION("Tổng quát hóa"),
    ACADEMIC_ARGUMENT("Lập luận học thuật");

    private final String label;

    ClaimType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

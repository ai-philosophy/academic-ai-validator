package com.aiacademy.academic;

import java.util.List;

public record ChecklistItem(
        String id,
        String title,
        String description,
        ChecklistPriority priority,
        String category,
        List<String> relatedClaimIds
) {
}

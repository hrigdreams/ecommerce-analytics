package com.ecommerce.analytics.dto.analytics.unified;

public record CustomerSummarySection(
        SectionMeta meta,
        long totalCustomers,
        long customersWithPaidOrders,
        long totalCategories
) {
}

package com.ecommerce.analytics.dto.analytics.unified;

import java.time.Instant;

// sanity check
public record SectionMeta(DataSource source, SectionStatus status, Instant asOf) {

    public static SectionMeta ok(DataSource source, Instant asOf) {
        return new SectionMeta(source, SectionStatus.OK, asOf);
    }

    public static SectionMeta empty(DataSource source) {
        return new SectionMeta(source, SectionStatus.EMPTY, null);
    }

    public static SectionMeta unavailable(DataSource source) {
        return new SectionMeta(source, SectionStatus.UNAVAILABLE, null);
    }
}

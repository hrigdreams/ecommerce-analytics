package com.ecommerce.analytics.dto.mapper;

/**
 * Generic entity -> DTO mapping contract, for the cases where a mapping
 * needs to be POLYMORPHIC/injectable (e.g. a generic "map this page of
 * entities" helper that doesn't know the concrete entity/DTO types at
 * compile time, or a future mapper that needs its own dependencies - a
 * repository lookup, a cache - injected via Spring rather than being a
 * static, dependency-free function).
 * <p>
 * This is deliberately a SEPARATE mechanism from the static
 * {@code fromEntity(...)} factory methods on the DTOs themselves (see
 * ProductResponse), not a replacement for them - Java static methods
 * cannot be declared here and "implemented" polymorphically the way
 * instance methods can (a static method in an implementing class doesn't
 * override anything from an interface; it would just happen to share a
 * name). For the common case used throughout this codebase today - one
 * concrete entity type mapping to one concrete DTO type, no extra
 * dependencies needed - the static factory on the DTO is simpler and is
 * what the refactored services call directly. This interface exists for
 * when that stops being enough, with {@link com.ecommerce.analytics.dto.ProductMapper}
 * as a (currently just delegating) example of wiring the two together.
 */
public interface EntityMapper<E, D> {

    D toDto(E entity);
}

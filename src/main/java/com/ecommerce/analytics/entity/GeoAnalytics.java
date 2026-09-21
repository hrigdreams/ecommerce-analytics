package com.ecommerce.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Rollup row per (country, city), fed by OrderAnalyticsService via the
 * ordering user's profile (users.country / users.city). "UNKNOWN" is used
 * when the user record or those fields are missing.
 */
@Entity
@Table(name = "geo_analytics", indexes = {
        @Index(name = "idx_geo_analytics_country_city", columnList = "country,city", unique = true)
})
public class GeoAnalytics {

    public static final String UNKNOWN = "UNKNOWN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country", nullable = false)
    private String country;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "orders", nullable = false)
    private long orders = 0;

    @Column(name = "paid_orders", nullable = false)
    private long paidOrders = 0;

    @Column(name = "revenue", nullable = false, precision = 14, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public GeoAnalytics() {
    }

    public Long getId() { return id; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public long getOrders() { return orders; }
    public void setOrders(long orders) { this.orders = orders; }

    public long getPaidOrders() { return paidOrders; }
    public void setPaidOrders(long paidOrders) { this.paidOrders = paidOrders; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

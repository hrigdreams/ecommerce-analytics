package com.ecommerce.analytics.datagen;

import java.util.Random;

/** Picks an index in [0, size) with weights that favor low indices (Zipf-like). */
final class Weighted {

    private final double[] cumulative;
    private final double total;

    /**
     * @param size    number of items
     * @param skew    higher = more concentrated on the first few items (Pareto-style
     *                "20% of products get 80% of sales"). 0.9-1.2 gives a realistic curve.
     */
    Weighted(int size, double skew) {
        cumulative = new double[size];
        double running = 0;
        for (int i = 0; i < size; i++) {
            running += 1.0 / Math.pow(i + 1, skew);
            cumulative[i] = running;
        }
        total = running;
    }

    int sample(Random random) {
        double target = random.nextDouble() * total;
        int lo = 0, hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (cumulative[mid] < target) lo = mid + 1; else hi = mid;
        }
        return lo;
    }
}

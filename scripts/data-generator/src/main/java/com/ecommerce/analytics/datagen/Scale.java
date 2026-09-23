package com.ecommerce.analytics.datagen;

/**
 * Presets for how much data to generate.
 *   small : the "prove the pipeline works" size from the project plan
 *           (10 users / 20 products / 50 orders / ~100 funnel events)
 *   large : the historical-baseline size
 *           (10,000 users / 5,000 products / 100,000 orders / ~300,000 items / 100,000 payments)
 * Pass a comma-separated override on the command line to use any other size,
 * e.g. "users=1000,products=500,orders=5000".
 */
public record Scale(int users, int products, int orders, int paymentsPercent) {

    public static final Scale SMALL = new Scale(10, 20, 50, 90);
    public static final Scale LARGE = new Scale(10_000, 5_000, 100_000, 90);

    public static Scale parse(String arg) {
        if (arg == null || arg.isBlank()) {
            return LARGE;
        }
        if (arg.equalsIgnoreCase("small")) {
            return SMALL;
        }
        if (arg.equalsIgnoreCase("large")) {
            return LARGE;
        }
        int users = LARGE.users();
        int products = LARGE.products();
        int orders = LARGE.orders();
        for (String part : arg.split(",")) {
            String[] kv = part.split("=");
            if (kv.length != 2) continue;
            switch (kv[0].trim()) {
                case "users" -> users = Integer.parseInt(kv[1].trim());
                case "products" -> products = Integer.parseInt(kv[1].trim());
                case "orders" -> orders = Integer.parseInt(kv[1].trim());
                default -> { /* ignore unknown key */ }
            }
        }
        return new Scale(users, products, orders, LARGE.paymentsPercent());
    }
}

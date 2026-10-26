package com.ecommerce.analytics.datagen;

/** Fixed reference lists used to build realistic-looking rows. */
final class RefData {

    private RefData() {}

    static final String[] FIRST_NAMES = {
            "Aarav", "Priya", "Wei", "Mei", "John", "Emma", "Liam", "Olivia", "Noah", "Ava",
            "Diego", "Sofia", "Hiro", "Yuki", "Amara", "Kwame", "Fatima", "Omar", "Ivan", "Elena",
            "Ravi", "Anjali", "Chen", "Ling", "James", "Sarah", "David", "Grace", "Ben", "Mia"
    };

    static final String[] LAST_NAMES = {
            "Sharma", "Patel", "Wang", "Li", "Smith", "Johnson", "Garcia", "Martinez", "Kim", "Lee",
            "Silva", "Rossi", "Tanaka", "Suzuki", "Okafor", "Mensah", "Khan", "Hassan", "Petrov", "Ivanova",
            "Gupta", "Verma", "Zhang", "Liu", "Brown", "Wilson", "Taylor", "Anderson", "Moore", "Clark"
    };

    /** {city, country, relative weight} - a handful of hubs, not evenly spread. */
    static final Object[][] GEO = {
            {"Kathmandu", "Nepal", 12},
            {"Mumbai", "India", 18},
            {"Bengaluru", "India", 14},
            {"New York", "USA", 15},
            {"San Francisco", "USA", 10},
            {"London", "UK", 12},
            {"Berlin", "Germany", 8},
            {"Singapore", "Singapore", 9},
            {"Tokyo", "Japan", 10},
            {"Sydney", "Australia", 7},
            {"Toronto", "Canada", 8},
            {"Sao Paulo", "Brazil", 9}
    };

    static final String[] GENDERS = {"Male", "Female", "Other"};
    static final int[] GENDER_WEIGHTS = {48, 48, 4};

    /**
     * {categoryName, minPrice, maxPrice}
     * <p>
     * PHASE 1 - item 2 (clean category seeding): this array's index order is
     * load-bearing. {@link #CATEGORY_PRODUCT_NOUNS}[i] is the set of product
     * nouns considered valid for {@code CATEGORIES[i]}, and DataGenerator
     * (seedCategories/seedProducts) relies on that same index alignment to
     * look up both price range and noun pool for a chosen category in one
     * shot. If you add, remove, or reorder a category here, update
     * CATEGORY_PRODUCT_NOUNS at the matching index too, or the two arrays go
     * out of sync and seedProducts will throw/mis-map.
     */
    static final Object[][] CATEGORIES = {
            {"Electronics", 15.0, 1500.0},
            {"Phones & Accessories", 10.0, 1200.0},
            {"Home & Kitchen", 8.0, 400.0},
            {"Fashion", 5.0, 250.0},
            {"Footwear", 15.0, 220.0},
            {"Beauty & Personal Care", 4.0, 90.0},
            {"Sports & Outdoors", 6.0, 600.0},
            {"Books", 3.0, 60.0},
            {"Toys & Games", 5.0, 150.0},
            {"Groceries", 1.0, 40.0},
            {"Furniture", 30.0, 2000.0},
            {"Automotive", 5.0, 900.0},
            {"Pet Supplies", 3.0, 120.0},
            {"Office Supplies", 2.0, 300.0},
            {"Health & Wellness", 4.0, 200.0}
    };

    /**
     * PHASE 3A FIX (data corruption item 1 - "Wireless Jacket"): kept only
     * as the union of every adjective used in {@link #CATEGORY_PRODUCT_ADJECTIVES}
     * below, for anything that wants "all adjectives regardless of
     * category" -- DataGenerator no longer reads from it directly for
     * product naming. Use {@link #CATEGORY_PRODUCT_ADJECTIVES} for that.
     * <p>
     * Before PHASE 3A, this flat list was the ONLY adjective source
     * seedProducts() used, picked completely independently of the category
     * (which, since PHASE 1, already picks its noun from a category-scoped
     * pool - see {@link #CATEGORY_PRODUCT_NOUNS}). That mismatch is exactly
     * how "Wireless Jacket" happened: "Wireless" is an Electronics-flavored
     * adjective, "Jacket" is a correctly-scoped Fashion noun, and nothing
     * stopped the two from being combined.
     */
    static final String[] PRODUCT_ADJECTIVES = {
            "Premium", "Compact", "Wireless", "Portable", "Classic", "Pro", "Essential", "Deluxe",
            "Everyday", "Lightweight", "Smart", "Eco", "Ultra", "Modern", "Rugged"
    };

    /**
     * PHASE 1 FIX (item 2 - "jackets in Office Supplies"): the original
     * seedProducts() picked a category index and a noun from this flat list
     * completely independently of each other, so any noun could land in any
     * category (e.g. "Premium Jacket" could be inserted under Office
     * Supplies). This flat array is kept only as the union of every noun
     * used below, for anything that wants "all nouns regardless of
     * category" -- DataGenerator no longer reads from it directly for
     * product naming. Use {@link #CATEGORY_PRODUCT_NOUNS} for that.
     */
    static final String[] PRODUCT_NOUNS = {
            "Charger", "Backpack", "Headphones", "Blender", "Lamp", "Jacket", "Sneakers", "Notebook",
            "Water Bottle", "Desk Organizer", "Speaker", "Watch", "Mug", "Keyboard", "Mouse",
            "Sunglasses", "Wallet", "Yoga Mat", "Cushion", "Board Game",
            "Smartwatch", "Phone Case", "Power Bank", "Kitchen Scale", "Scarf",
            "Running Shoes", "Sandals", "Face Cream", "Hair Dryer", "Electric Razor",
            "Camping Tent", "Novel", "Journal", "Puzzle", "Action Figure",
            "Coffee Beans", "Snack Pack", "Tea Set", "Bookshelf", "Office Chair",
            "Car Charger", "Seat Cover", "Dash Cam", "Pet Bed", "Leash", "Pet Bowl",
            "Stapler", "Vitamin Pack"
    };

    /**
     * PHASE 1 FIX (item 2): product nouns grouped by the category they
     * actually belong to, index-aligned with {@link #CATEGORIES}.
     * DataGenerator.seedProducts() now picks the category first (as before),
     * then picks the noun from {@code CATEGORY_PRODUCT_NOUNS[categoryIndex]}
     * instead of from the unrelated flat PRODUCT_NOUNS list -- so a product
     * filed under "Office Supplies" can only ever be named from this
     * category's own pool (Desk Organizer, Notebook, Stapler, ...), never
     * "Jacket".
     */
    static final String[][] CATEGORY_PRODUCT_NOUNS = {
            // 0 Electronics
            {"Charger", "Headphones", "Speaker", "Keyboard", "Mouse", "Smartwatch", "Power Bank"},
            // 1 Phones & Accessories
            {"Charger", "Headphones", "Speaker", "Phone Case", "Power Bank"},
            // 2 Home & Kitchen
            {"Blender", "Lamp", "Mug", "Cushion", "Kitchen Scale"},
            // 3 Fashion
            {"Jacket", "Wallet", "Sunglasses", "Watch", "Scarf"},
            // 4 Footwear
            {"Sneakers", "Running Shoes", "Sandals"},
            // 5 Beauty & Personal Care
            {"Face Cream", "Hair Dryer", "Electric Razor"},
            // 6 Sports & Outdoors
            {"Yoga Mat", "Water Bottle", "Backpack", "Camping Tent"},
            // 7 Books
            {"Notebook", "Novel", "Journal"},
            // 8 Toys & Games
            {"Board Game", "Puzzle", "Action Figure"},
            // 9 Groceries
            {"Coffee Beans", "Snack Pack", "Tea Set"},
            // 10 Furniture
            {"Desk Organizer", "Bookshelf", "Office Chair"},
            // 11 Automotive
            {"Car Charger", "Seat Cover", "Dash Cam"},
            // 12 Pet Supplies
            {"Pet Bed", "Leash", "Pet Bowl"},
            // 13 Office Supplies
            {"Desk Organizer", "Notebook", "Stapler"},
            // 14 Health & Wellness
            {"Yoga Mat", "Water Bottle", "Vitamin Pack"}
    };

    /**
     * PHASE 3A FIX (data corruption item 1 - "Wireless Jacket"): adjectives
     * grouped by the category they actually fit, index-aligned with
     * {@link #CATEGORIES} (and therefore also with {@link #CATEGORY_PRODUCT_NOUNS} -
     * all three arrays share the same category index for a given position).
     * DataGenerator.seedProducts() now picks BOTH the adjective and the noun
     * from this same category's own pools, so "Electronics" can only ever
     * produce adjectives like Wireless/Smart/Compact, and "Fashion" only
     * Classic/Rugged/Essential-style ones - never a cross-category mix like
     * "Wireless Jacket".
     * <p>
     * Deliberately not a strict partition: a few adjectives (e.g. "Premium",
     * "Classic", "Essential") legitimately fit several categories and
     * reappear in more than one pool - that is fine and expected, the
     * invariant that actually matters (and that {@code RefDataCategoryAdjectiveTest}
     * checks) is that no category's pool contains an adjective that reads as
     * wrong for it, not that every pool is disjoint the way the noun pools are.
     */
    static final String[][] CATEGORY_PRODUCT_ADJECTIVES = {
            // 0 Electronics
            {"Wireless", "Smart", "Compact", "Portable", "Pro", "Ultra"},
            // 1 Phones & Accessories
            {"Wireless", "Smart", "Compact", "Portable", "Ultra"},
            // 2 Home & Kitchen
            {"Modern", "Compact", "Essential", "Everyday", "Premium"},
            // 3 Fashion
            {"Classic", "Rugged", "Essential", "Premium", "Modern"},
            // 4 Footwear
            {"Classic", "Rugged", "Lightweight", "Everyday", "Pro"},
            // 5 Beauty & Personal Care
            {"Essential", "Everyday", "Premium", "Eco", "Deluxe"},
            // 6 Sports & Outdoors
            {"Rugged", "Lightweight", "Pro", "Portable", "Essential"},
            // 7 Books
            {"Classic", "Essential", "Everyday"},
            // 8 Toys & Games
            {"Classic", "Deluxe", "Everyday", "Compact"},
            // 9 Groceries
            {"Essential", "Everyday", "Eco", "Premium"},
            // 10 Furniture
            {"Modern", "Classic", "Compact", "Premium", "Rugged"},
            // 11 Automotive
            {"Rugged", "Pro", "Essential", "Compact"},
            // 12 Pet Supplies
            {"Essential", "Everyday", "Rugged", "Deluxe"},
            // 13 Office Supplies
            {"Essential", "Compact", "Everyday", "Modern"},
            // 14 Health & Wellness
            {"Essential", "Everyday", "Eco", "Premium", "Deluxe"}
    };

    static final String[] PAYMENT_METHODS = {"CARD", "PAYPAL", "UPI", "WALLET", "BANK_TRANSFER"};
}

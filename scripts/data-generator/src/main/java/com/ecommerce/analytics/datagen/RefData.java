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

    /** {categoryName, minPrice, maxPrice} */
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

    static final String[] PRODUCT_ADJECTIVES = {
            "Premium", "Compact", "Wireless", "Portable", "Classic", "Pro", "Essential", "Deluxe",
            "Everyday", "Lightweight", "Smart", "Eco", "Ultra", "Modern", "Rugged"
    };

    static final String[] PRODUCT_NOUNS = {
            "Charger", "Backpack", "Headphones", "Blender", "Lamp", "Jacket", "Sneakers", "Notebook",
            "Water Bottle", "Desk Organizer", "Speaker", "Watch", "Mug", "Keyboard", "Mouse",
            "Sunglasses", "Wallet", "Yoga Mat", "Cushion", "Board Game"
    };

    static final String[] PAYMENT_METHODS = {"CARD", "PAYPAL", "UPI", "WALLET", "BANK_TRANSFER"};
}

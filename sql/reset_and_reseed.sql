-- =====================================================================
-- PHASE 1, item 2: clean reset + category-accurate reseed
-- =====================================================================
--
-- WHAT THIS SCRIPT DOES vs. WHAT IT DELIBERATELY LEAVES TO DataGenerator
-- ---------------------------------------------------------------------

--
-- DataGenerator's existing `--reset` flag already truncates every
-- relational + JPA-analytics table (orders, order_items, products,
-- payments, carts, cart_items, reviews, categories, users, plus all the
-- *_analytics tables) and then re-seeds users/categories/products/orders
-- through real JDBC batch inserts with generated keys, which is what
-- gives you actual referential integrity (FKs populated from real
-- RETURN_GENERATED_KEYS values, not guessed ids). Reimplementing that
-- insert logic a second time in raw SQL here would be a second, divergent
-- source of truth for the same data and would not fix anything --
-- DataGenerator.seedProducts() already got the real fix (see
-- scripts/data-generator/.../RefData.java + DataGenerator.java in this
-- same delivery: products are now named from a noun pool scoped to their
-- own category, so "Jacket" can no longer land under "Office Supplies").
--
-- So, by design, this script's job is the one piece DataGenerator's
-- --reset does NOT cover: the spark_batch_*/spark_streaming_* tables.
-- Those are written directly by the separate spark-analytics JVM process
-- (SparkBatchAnalyticsJob / SparkStreamingAnalyticsJob) via
-- df.write().mode(SaveMode.Overwrite) -- they are plain Postgres tables
-- with no JPA entity and no FK to anything DataGenerator touches, which
-- is exactly why the user's Phase 1 request calls them out separately
-- from "orders, order_items, products".
--
-- RUN ORDER:
--   1. This script (clears the spark_* tables; safe to run any time,
--      including before the app/Spark have ever run -- uses
--      TRUNCATE TABLE IF EXISTS-style guards via DO blocks so a table
--      that hasn't been created yet is skipped, not an error).
--   2. `java -jar data-generator.jar <scale> --reset` (truncates +
--      re-seeds users/categories/products/orders/payments with the
--      category-noun fix applied).
--   3. Re-run the Spark batch job (run-batch.sh / run-batch-loop.sh) to
--      repopulate spark_batch_* from the freshly reseeded orders.
--   4. Start the streaming job / simulator to repopulate spark_streaming_*.
--
-- This script does NOT touch processed_events, product_analytics, or any
-- other table DataGenerator's own --reset already owns -- doing so here
-- too would just race the two resets against each other.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Purge spark_batch_* / spark_streaming_* (the gap in DataGenerator's
--    own --reset). Each table is dropped-and-will-be-recreated by Spark's
--    own SaveMode.Overwrite on its next run, but TRUNCATE is used instead
--    of DROP so a table that doesn't exist yet simply raises a notice and
--    is skipped -- this script is safe to run before Spark has ever run.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    tbl text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'spark_batch_product_performance',
        'spark_batch_category_performance',
        'spark_batch_customer_behavior',
        'spark_batch_revenue_trend',
        'spark_batch_geo_performance',
        'spark_batch_hourly_pattern',
        'spark_batch_product_popularity',
        'spark_streaming_event_counts',
        'spark_streaming_order_activity',
        'spark_streaming_revenue'
    ]
    LOOP
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = tbl
        ) THEN
            EXECUTE format('TRUNCATE TABLE %I', tbl);
            RAISE NOTICE 'Truncated %', tbl;
        ELSE
            RAISE NOTICE 'Skipped % (table does not exist yet -- Spark job has never run)', tbl;
        END IF;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- 2. Reference check: the 15 canonical category names, in the exact
--    order DataGenerator.seedCategories() / RefData.CATEGORIES expects.
--    This SELECT is informational only (run it yourself after step 2
--    above to confirm the reseed matches) -- it does not insert anything,
--    since category insertion with correct generated ids is
--    DataGenerator's job, not raw SQL's, per the rationale above.
-- ---------------------------------------------------------------------
-- Expected categories after `data-generator.jar ... --reset`:
--   Electronics, Phones & Accessories, Home & Kitchen, Fashion, Footwear,
--   Beauty & Personal Care, Sports & Outdoors, Books, Toys & Games,
--   Groceries, Furniture, Automotive, Pet Supplies, Office Supplies,
--   Health & Wellness
--
-- SELECT id, name FROM categories ORDER BY id;

-- ---------------------------------------------------------------------
-- 3. Sanity check you can run post-reseed to confirm no product's noun
--    mismatches its category (belt-and-suspenders on top of the code
--    fix -- this should always return zero rows once step 2 above has
--    been run with the patched DataGenerator). Flags any product whose
--    name contains a noun that belongs to a *different* category's pool
--    than the one it's actually filed under, for the handful of nouns
--    that are unambiguous to one category (jackets, sneakers, blenders).
-- ---------------------------------------------------------------------
-- SELECT p.id, p.name, c.name AS filed_under
-- FROM products p
-- JOIN categories c ON c.id = p.category_id
-- WHERE (p.name ILIKE '%jacket%'   AND c.name <> 'Fashion')
--    OR (p.name ILIKE '%sneakers%' AND c.name <> 'Footwear')
--    OR (p.name ILIKE '%blender%'  AND c.name <> 'Home & Kitchen')
--    OR (p.name ILIKE '%stapler%'  AND c.name <> 'Office Supplies')
--    OR (p.name ILIKE '%pet bed%'  AND c.name <> 'Pet Supplies');

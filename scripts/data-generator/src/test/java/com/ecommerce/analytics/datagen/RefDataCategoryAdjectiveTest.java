package com.ecommerce.analytics.datagen;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PHASE 3A, data corruption item 1 ("Wireless Jacket" fix). Guards the
 * structural invariant DataGenerator.seedProducts() now depends on for
 * adjectives, mirroring {@link RefDataCategoryNounTest} for nouns:
 * RefData.CATEGORY_PRODUCT_ADJECTIVES must be index-aligned with
 * RefData.CATEGORIES (same length, no gaps, no empty pools) - if that ever
 * drifts, this fails loudly at test time instead of silently mis-pairing
 * adjectives and categories again in production.
 */
class RefDataCategoryAdjectiveTest {

    @Test
    void categoryProductAdjectives_shouldBeIndexAlignedWithCategories() {
        assertEquals(
                RefData.CATEGORIES.length,
                RefData.CATEGORY_PRODUCT_ADJECTIVES.length,
                "CATEGORY_PRODUCT_ADJECTIVES must have exactly one adjective-pool entry per CATEGORIES entry, same order"
        );
    }

    @Test
    void everyCategory_shouldHaveAtLeastOneAdjective() {
        for (int i = 0; i < RefData.CATEGORY_PRODUCT_ADJECTIVES.length; i++) {
            String categoryName = (String) RefData.CATEGORIES[i][0];
            assertFalse(
                    RefData.CATEGORY_PRODUCT_ADJECTIVES[i].length == 0,
                    "Category '" + categoryName + "' (index " + i + ") has an empty adjective pool"
            );
        }
    }

    @Test
    void everyAdjectiveInEveryCategoryPool_shouldComeFromTheFlatProductAdjectivesUnion() {
        Set<String> flatUnion = new HashSet<>(Arrays.asList(RefData.PRODUCT_ADJECTIVES));
        for (String[] pool : RefData.CATEGORY_PRODUCT_ADJECTIVES) {
            for (String adjective : pool) {
                assertTrue(
                        flatUnion.contains(adjective),
                        "'" + adjective + "' is used in a category pool but missing from the flat PRODUCT_ADJECTIVES union"
                );
            }
        }
    }

    @Test
    void electronicsAndFashion_shouldNotShareTheExactSameAdjectivePool() {
        // Regression guard for the exact reported bug ("Wireless Jacket"):
        // "Wireless" must be an Electronics-fitting adjective and must NOT
        // appear in Fashion's pool, so a Fashion noun ("Jacket") can never
        // be combined with it again via seedProducts().
        int electronicsIndex = indexOfCategory("Electronics");
        int fashionIndex = indexOfCategory("Fashion");

        Set<String> electronicsAdjectives = new HashSet<>(Arrays.asList(RefData.CATEGORY_PRODUCT_ADJECTIVES[electronicsIndex]));
        Set<String> fashionAdjectives = new HashSet<>(Arrays.asList(RefData.CATEGORY_PRODUCT_ADJECTIVES[fashionIndex]));

        assertTrue(electronicsAdjectives.contains("Wireless"), "sanity check: Wireless should be an Electronics adjective");
        assertFalse(fashionAdjectives.contains("Wireless"), "regression check: Wireless must NOT be a Fashion adjective");
    }

    @Test
    void adjectivePoolIndex_shouldLineUpWithTheSameCategoryAsTheNounPoolIndex() {
        // Both pools are index-aligned with RefData.CATEGORIES independently,
        // but seedProducts() relies on picking from BOTH pools using the
        // SAME categoryIndex in one iteration - this just re-confirms the
        // two arrays have the same length as each other (not just each
        // individually matching CATEGORIES.length), so a future edit to one
        // without the other is caught here too.
        assertEquals(
                RefData.CATEGORY_PRODUCT_NOUNS.length,
                RefData.CATEGORY_PRODUCT_ADJECTIVES.length,
                "CATEGORY_PRODUCT_NOUNS and CATEGORY_PRODUCT_ADJECTIVES must have the same length - "
                        + "seedProducts() indexes both with the same categoryIndex"
        );
    }

    private static int indexOfCategory(String name) {
        for (int i = 0; i < RefData.CATEGORIES.length; i++) {
            if (name.equals(RefData.CATEGORIES[i][0])) {
                return i;
            }
        }
        throw new IllegalArgumentException("No such category: " + name);
    }
}

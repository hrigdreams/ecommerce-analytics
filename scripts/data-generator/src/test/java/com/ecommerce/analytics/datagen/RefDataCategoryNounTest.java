package com.ecommerce.analytics.datagen;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PHASE 1, item 2 ("jackets in Office Supplies" fix). Guards the structural
 * invariant DataGenerator.seedProducts() now depends on:
 * RefData.CATEGORY_PRODUCT_NOUNS must be index-aligned with RefData.CATEGORIES
 * (same length, no gaps, no empty pools) - if that ever drifts (a category
 * added/removed without updating the noun groups, or vice versa), this
 * fails loudly at test time instead of silently mis-pairing products and
 * categories again in production.
 *
 * NOTE: this module previously had no JUnit dependency or Surefire plugin at
 * all (it's a standalone `java -jar` CLI tool, no tests existed before
 * Phase 1) - scripts/data-generator/pom.xml was updated in this same
 * delivery to add both, specifically so this test can run via `mvn test`.
 */
class RefDataCategoryNounTest {

    @Test
    void categoryProductNouns_shouldBeIndexAlignedWithCategories() {
        assertEquals(
                RefData.CATEGORIES.length,
                RefData.CATEGORY_PRODUCT_NOUNS.length,
                "CATEGORY_PRODUCT_NOUNS must have exactly one noun-pool entry per CATEGORIES entry, same order"
        );
    }

    @Test
    void everyCategory_shouldHaveAtLeastOneNoun() {
        for (int i = 0; i < RefData.CATEGORY_PRODUCT_NOUNS.length; i++) {
            String categoryName = (String) RefData.CATEGORIES[i][0];
            assertFalse(
                    RefData.CATEGORY_PRODUCT_NOUNS[i].length == 0,
                    "Category '" + categoryName + "' (index " + i + ") has an empty noun pool"
            );
        }
    }

    @Test
    void everyNounInEveryCategoryPool_shouldComeFromTheFlatProductNounsUnion() {
        Set<String> flatUnion = new HashSet<>(Arrays.asList(RefData.PRODUCT_NOUNS));
        for (String[] pool : RefData.CATEGORY_PRODUCT_NOUNS) {
            for (String noun : pool) {
                assertTrue(
                        flatUnion.contains(noun),
                        "'" + noun + "' is used in a category pool but missing from the flat PRODUCT_NOUNS union"
                );
            }
        }
    }

    @Test
    void fashionAndOfficeSupplies_shouldNotShareTheSameNounPool() {
        // Regression guard for the exact reported bug ("jackets in Office
        // Supplies"): Fashion's and Office Supplies' noun pools must be
        // disjoint, so a product named from one can never be filed under
        // the other's category index.
        int fashionIndex = indexOfCategory("Fashion");
        int officeSuppliesIndex = indexOfCategory("Office Supplies");

        Set<String> fashionNouns = new HashSet<>(Arrays.asList(RefData.CATEGORY_PRODUCT_NOUNS[fashionIndex]));
        Set<String> officeNouns = new HashSet<>(Arrays.asList(RefData.CATEGORY_PRODUCT_NOUNS[officeSuppliesIndex]));

        Set<String> intersection = new HashSet<>(fashionNouns);
        intersection.retainAll(officeNouns);

        assertTrue(intersection.isEmpty(),
                "Fashion and Office Supplies noun pools overlap: " + intersection);
        assertTrue(fashionNouns.contains("Jacket"), "sanity check: Jacket should be a Fashion noun");
        assertFalse(officeNouns.contains("Jacket"), "regression check: Jacket must NOT be an Office Supplies noun");
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

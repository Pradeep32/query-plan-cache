package com.queryplancache.normalizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SqlQueryNormalizer Tests")
class SqlQueryNormalizerTest {

    private SqlQueryNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new SqlQueryNormalizer();
    }

    // =========================================================================
    // Normal Cases: Query Normalization
    // =========================================================================
    @Nested
    @DisplayName("Query Normalization — Normal Cases")
    class NormalCases {

        @Test
        @DisplayName("SELECT with integer WHERE clause")
        void shouldNormalizeSelectWithIntegerLiteral() {
            ParameterBinding result = normalizer.normalize(
                    "SELECT * FROM orders WHERE customer_id = 101");

            assertEquals("SELECT * FROM orders WHERE customer_id = ?",
                    result.getNormalizedQuery());
            assertEquals(1, result.getParameterCount());
            assertEquals(101L, result.getParameters().get(0));
        }

        @Test
        @DisplayName("SELECT with string WHERE clause")
        void shouldNormalizeSelectWithStringLiteral() {
            ParameterBinding result = normalizer.normalize(
                    "SELECT * FROM users WHERE name = 'John Doe'");

            assertEquals("SELECT * FROM users WHERE name = ?",
                    result.getNormalizedQuery());
            assertEquals(1, result.getParameterCount());
            assertEquals("John Doe", result.getParameters().get(0));
        }

        @Test
        @DisplayName("SELECT with multiple mixed-type parameters")
        void shouldNormalizeMultipleParameters() {
            ParameterBinding result = normalizer.normalize(
                    "SELECT name FROM users WHERE age > 25 AND city = 'NYC'");

            assertEquals("SELECT name FROM users WHERE age > ? AND city = ?",
                    result.getNormalizedQuery());
            assertEquals(2, result.getParameterCount());
            assertEquals(25L, result.getParameters().get(0));
            assertEquals("NYC", result.getParameters().get(1));
        }

        @Test
        @DisplayName("INSERT with multiple values")
        void shouldNormalizeInsertStatement() {
            ParameterBinding result = normalizer.normalize(
                    "INSERT INTO logs VALUES (1, 'error', 1724684407)");

            assertEquals("INSERT INTO logs VALUES (?, ?, ?)",
                    result.getNormalizedQuery());
            assertEquals(3, result.getParameterCount());
        }

        @Test
        @DisplayName("UPDATE with SET and WHERE")
        void shouldNormalizeUpdateStatement() {
            ParameterBinding result = normalizer.normalize(
                    "UPDATE users SET age = 30 WHERE id = 5");

            assertEquals("UPDATE users SET age = ? WHERE id = ?",
                    result.getNormalizedQuery());
            assertEquals(2, result.getParameterCount());
        }

        @Test
        @DisplayName("DELETE with WHERE clause")
        void shouldNormalizeDeleteStatement() {
            ParameterBinding result = normalizer.normalize(
                    "DELETE FROM orders WHERE id = 999");

            assertEquals("DELETE FROM orders WHERE id = ?",
                    result.getNormalizedQuery());
            assertEquals(1, result.getParameterCount());
            assertEquals(999L, result.getParameters().get(0));
        }

        @Test
        @DisplayName("Float literal normalization")
        void shouldNormalizeFloatLiterals() {
            ParameterBinding result = normalizer.normalize(
                    "SELECT * FROM products WHERE price > 19.99");

            assertEquals("SELECT * FROM products WHERE price > ?",
                    result.getNormalizedQuery());
            assertEquals(1, result.getParameterCount());
            assertEquals(19.99, result.getParameters().get(0));
        }

        @Test
        @DisplayName("Query with no literals returns unchanged")
        void shouldHandleQueryWithNoLiterals() {
            ParameterBinding result = normalizer.normalize(
                    "SELECT * FROM orders");

            assertEquals("SELECT * FROM orders", result.getNormalizedQuery());
            assertEquals(0, result.getParameterCount());
        }
    }

    // =========================================================================
    // Structural Equivalence
    // =========================================================================
    @Nested
    @DisplayName("Structural Equivalence — Same Pattern, Different Values")
    class StructuralEquivalence {

        @Test
        @DisplayName("Queries with same structure produce identical normalized forms")
        void shouldProduceSameNormalizedForm() {
            ParameterBinding result1 = normalizer.normalize(
                    "SELECT * FROM orders WHERE customer_id = 101");
            ParameterBinding result2 = normalizer.normalize(
                    "SELECT * FROM orders WHERE customer_id = 202");

            assertEquals(result1.getNormalizedQuery(), result2.getNormalizedQuery());
            assertNotEquals(result1.getParameters(), result2.getParameters());
        }

        @Test
        @DisplayName("Different query structures produce different normalized forms")
        void shouldProduceDifferentNormalizedForms() {
            ParameterBinding result1 = normalizer.normalize(
                    "SELECT * FROM orders WHERE customer_id = 101");
            ParameterBinding result2 = normalizer.normalize(
                    "SELECT * FROM orders WHERE order_date = '2024-01-01'");

            assertNotEquals(result1.getNormalizedQuery(), result2.getNormalizedQuery());
        }
    }

    // =========================================================================
    // Canonicalization
    // =========================================================================
    @Nested
    @DisplayName("Canonicalization — Whitespace and Formatting")
    class Canonicalization {

        @Test
        @DisplayName("Normalizes extra whitespace")
        void shouldNormalizeExtraWhitespace() {
            ParameterBinding result = normalizer.normalize(
                    "SELECT  *   FROM   orders   WHERE   id = 1");

            assertEquals("SELECT * FROM orders WHERE id = ?",
                    result.getNormalizedQuery());
        }

        @Test
        @DisplayName("Strips trailing semicolons")
        void shouldStripTrailingSemicolon() {
            ParameterBinding result = normalizer.normalize(
                    "SELECT * FROM orders WHERE id = 1;");

            assertEquals("SELECT * FROM orders WHERE id = ?",
                    result.getNormalizedQuery());
        }

        @Test
        @DisplayName("Trims leading/trailing whitespace")
        void shouldTrimWhitespace() {
            ParameterBinding result = normalizer.normalize(
                    "  SELECT * FROM orders WHERE id = 1  ");

            assertEquals("SELECT * FROM orders WHERE id = ?",
                    result.getNormalizedQuery());
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Throws on null input")
        void shouldThrowOnNullInput() {
            assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(null));
        }

        @Test
        @DisplayName("Throws on blank input")
        void shouldThrowOnBlankInput() {
            assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("   "));
        }

        @Test
        @DisplayName("Handles negative numbers")
        void shouldHandleNegativeNumbers() {
            ParameterBinding result = normalizer.normalize(
                    "SELECT * FROM accounts WHERE balance > -100");

            assertEquals("SELECT * FROM accounts WHERE balance > ?",
                    result.getNormalizedQuery());
            assertEquals(-100L, result.getParameters().get(0));
        }

        @Test
        @DisplayName("Handles IN clause with multiple values")
        void shouldHandleInClause() {
            ParameterBinding result = normalizer.normalize(
                    "SELECT * FROM orders WHERE id IN (1, 2, 3)");

            assertEquals("SELECT * FROM orders WHERE id IN (?, ?, ?)",
                    result.getNormalizedQuery());
            assertEquals(3, result.getParameterCount());
        }

        @Test
        @DisplayName("Handles BETWEEN clause")
        void shouldHandleBetweenClause() {
            ParameterBinding result = normalizer.normalize(
                    "SELECT * FROM orders WHERE price BETWEEN 10 AND 50");

            assertEquals("SELECT * FROM orders WHERE price BETWEEN ? AND ?",
                    result.getNormalizedQuery());
            assertEquals(2, result.getParameterCount());
        }
    }

    // =========================================================================
    // DDL Detection
    // =========================================================================
    @Nested
    @DisplayName("DDL Detection")
    class DdlDetection {

        @ParameterizedTest
        @ValueSource(strings = {
                "CREATE TABLE users (id INT)",
                "ALTER TABLE orders ADD COLUMN status VARCHAR(50)",
                "DROP TABLE temp_data",
                "CREATE INDEX idx_name ON users(name)"
        })
        @DisplayName("Correctly identifies DDL statements")
        void shouldDetectDdl(String ddl) {
            assertTrue(normalizer.isDdlStatement(ddl));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "SELECT * FROM orders",
                "INSERT INTO logs VALUES (1)",
                "UPDATE users SET name = 'test'",
                "DELETE FROM orders WHERE id = 1"
        })
        @DisplayName("Correctly identifies non-DDL statements")
        void shouldRejectNonDdl(String dml) {
            assertFalse(normalizer.isDdlStatement(dml));
        }

        @ParameterizedTest
        @CsvSource({
                "'ALTER TABLE orders ADD COLUMN status VARCHAR(50)', orders",
                "'DROP TABLE temp_data', temp_data",
                "'CREATE INDEX idx_name ON users(name)', idx_name"
        })
        @DisplayName("Extracts target table from DDL")
        void shouldExtractDdlTargetTable(String ddl, String expectedTable) {
            assertEquals(expectedTable, normalizer.extractDdlTargetTable(ddl));
        }
    }
}

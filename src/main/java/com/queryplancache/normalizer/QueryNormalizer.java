package com.queryplancache.normalizer;

/**
 * Interface for query normalization strategies.
 * Implementations transform raw SQL queries into canonical forms
 * suitable for cache key generation.
 */
public interface QueryNormalizer {

    /**
     * Normalizes a raw SQL query by replacing literal values with placeholders
     * and extracting the original values as parameter bindings.
     *
     * @param rawSql the original SQL query string
     * @return a ParameterBinding containing the normalized query and extracted parameters
     */
    ParameterBinding normalize(String rawSql);

    /**
     * Determines if the given SQL is a DDL statement (CREATE, ALTER, DROP)
     * that should trigger cache invalidation.
     *
     * @param rawSql the SQL statement to classify
     * @return true if the statement is a DDL operation
     */
    boolean isDdlStatement(String rawSql);

    /**
     * Extracts the table name affected by a DDL statement.
     *
     * @param rawSql the DDL statement
     * @return the affected table name, or null if not determinable
     */
    String extractDdlTargetTable(String rawSql);
}

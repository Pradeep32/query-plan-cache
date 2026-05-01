package com.queryplancache.normalizer;

import java.util.List;

/**
 * Holds the result of query normalization: the normalized query string
 * and the extracted parameter bindings (original literal values).
 */
public class ParameterBinding {

    private final String normalizedQuery;
    private final List<Object> parameters;

    public ParameterBinding(String normalizedQuery, List<Object> parameters) {
        this.normalizedQuery = normalizedQuery;
        this.parameters = List.copyOf(parameters); // Immutable copy
    }

    /**
     * Returns the normalized query with all literals replaced by '?' placeholders.
     * Example: "SELECT * FROM orders WHERE customer_id = ?"
     */
    public String getNormalizedQuery() {
        return normalizedQuery;
    }

    /**
     * Returns the extracted parameter values in order of their appearance.
     * Example: [101] for "SELECT * FROM orders WHERE customer_id = 101"
     */
    public List<Object> getParameters() {
        return parameters;
    }

    /**
     * Returns the number of parameters extracted.
     */
    public int getParameterCount() {
        return parameters.size();
    }

    @Override
    public String toString() {
        return "ParameterBinding{normalizedQuery='" + normalizedQuery
                + "', parameters=" + parameters + '}';
    }
}

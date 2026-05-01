package com.queryplancache.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simulates query plan generation.
 *
 * In a real database system, this would invoke the query optimizer to produce
 * an execution plan. Here, we simulate the process with a configurable delay
 * to measure caching performance improvements.
 */
public class QueryPlanGenerator {

    private static final Logger log = LoggerFactory.getLogger(QueryPlanGenerator.class);
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?i)(?:FROM|INTO|UPDATE|JOIN)\\s+(\\w+)");

    private final long simulatedPlanGenerationTimeMs;

    public QueryPlanGenerator() {
        this(50); // Default: 50ms simulated plan generation time
    }

    public QueryPlanGenerator(long simulatedPlanGenerationTimeMs) {
        this.simulatedPlanGenerationTimeMs = simulatedPlanGenerationTimeMs;
    }

    /**
     * Generates a query execution plan for the given normalized query.
     * Simulates the cost of plan generation with a configurable delay.
     *
     * @param normalizedQuery the normalized SQL query with placeholders
     * @return a QueryPlan representing the execution strategy
     */
    public QueryPlan generatePlan(String normalizedQuery) {
        log.debug("Generating execution plan for: {}", normalizedQuery);
        long startTime = System.nanoTime();

        // Simulate expensive plan generation (optimizer, cost estimation, etc.)
        simulateOptimizationCost();

        // Extract referenced tables from the normalized query
        Set<String> referencedTables = extractReferencedTables(normalizedQuery);

        // Determine plan type from the query
        String planType = determinePlanType(normalizedQuery);

        // Build a simulated execution plan tree
        PlanOperation rootOperation = buildPlanTree(normalizedQuery, planType, referencedTables);

        double totalCost = rootOperation.getCostEstimate();

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        log.info("Plan generated in {}ms for query: {}", elapsedMs, normalizedQuery);

        return new QueryPlan(normalizedQuery, planType, rootOperation, referencedTables, totalCost);
    }

    private void simulateOptimizationCost() {
        try {
            Thread.sleep(simulatedPlanGenerationTimeMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String determinePlanType(String normalizedQuery) {
        String upper = normalizedQuery.trim().toUpperCase();
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        return "UNKNOWN";
    }

    Set<String> extractReferencedTables(String normalizedQuery) {
        Set<String> tables = new java.util.LinkedHashSet<>();
        Matcher matcher = TABLE_PATTERN.matcher(normalizedQuery);
        while (matcher.find()) {
            tables.add(matcher.group(1).toLowerCase());
        }
        return tables;
    }

    private PlanOperation buildPlanTree(String normalizedQuery, String planType,
                                        Set<String> referencedTables) {
        String primaryTable = referencedTables.isEmpty() ? "unknown" : referencedTables.iterator().next();

        // Build a simple two-level plan: Scan -> Filter/Project
        PlanOperation scanOp = new PlanOperation(
                "TABLE_SCAN", primaryTable, List.of("*"),
                null, 10000, 100.0
        );

        if (normalizedQuery.toUpperCase().contains("WHERE")) {
            PlanOperation filterOp = new PlanOperation(
                    "FILTER", primaryTable, List.of("*"),
                    "predicate(?) ", 100, 10.0
            );
            filterOp.setChildren(List.of(scanOp));

            PlanOperation projectOp = new PlanOperation(
                    "PROJECT", null, List.of("*"),
                    null, 100, 5.0
            );
            projectOp.setChildren(List.of(filterOp));
            return projectOp;
        }

        return scanOp;
    }
}

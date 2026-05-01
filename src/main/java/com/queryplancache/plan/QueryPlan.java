package com.queryplancache.plan;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;
import java.util.Set;

/**
 * Represents a complete query execution plan as a JSON-serializable object.
 * The plan describes the step-by-step execution strategy for a normalized query.
 */
public class QueryPlan {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @JsonProperty("normalizedQuery")
    private String normalizedQuery;

    @JsonProperty("planType")
    private String planType;        // e.g., "SELECT", "INSERT", "UPDATE", "DELETE"

    @JsonProperty("rootOperation")
    private PlanOperation rootOperation;

    @JsonProperty("referencedTables")
    private Set<String> referencedTables;

    @JsonProperty("estimatedTotalCost")
    private double estimatedTotalCost;

    @JsonProperty("optimizerHints")
    private List<String> optimizerHints;

    public QueryPlan() {}

    public QueryPlan(String normalizedQuery, String planType,
                     PlanOperation rootOperation, Set<String> referencedTables,
                     double estimatedTotalCost) {
        this.normalizedQuery = normalizedQuery;
        this.planType = planType;
        this.rootOperation = rootOperation;
        this.referencedTables = referencedTables;
        this.estimatedTotalCost = estimatedTotalCost;
    }

    public String getNormalizedQuery() { return normalizedQuery; }
    public void setNormalizedQuery(String normalizedQuery) { this.normalizedQuery = normalizedQuery; }

    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }

    public PlanOperation getRootOperation() { return rootOperation; }
    public void setRootOperation(PlanOperation rootOperation) { this.rootOperation = rootOperation; }

    public Set<String> getReferencedTables() { return referencedTables; }
    public void setReferencedTables(Set<String> referencedTables) { this.referencedTables = referencedTables; }

    public double getEstimatedTotalCost() { return estimatedTotalCost; }
    public void setEstimatedTotalCost(double estimatedTotalCost) { this.estimatedTotalCost = estimatedTotalCost; }

    public List<String> getOptimizerHints() { return optimizerHints; }
    public void setOptimizerHints(List<String> optimizerHints) { this.optimizerHints = optimizerHints; }

    /**
     * Serializes this query plan to a JSON string.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Deserializes a JSON string into a QueryPlan object.
     */
    public static QueryPlan fromJson(String json) {
        try {
            return MAPPER.readValue(json, QueryPlan.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse query plan JSON", e);
        }
    }
}

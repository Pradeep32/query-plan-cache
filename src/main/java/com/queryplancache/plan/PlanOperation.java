package com.queryplancache.plan;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a single operation in a query execution plan.
 * Operations form a tree structure (e.g., Scan -> Filter -> Project).
 */
public class PlanOperation {

    @JsonProperty("type")
    private String type;          // e.g., "TABLE_SCAN", "INDEX_SCAN", "FILTER", "JOIN", "AGGREGATE", "PROJECT"

    @JsonProperty("table")
    private String table;         // Target table (if applicable)

    @JsonProperty("columns")
    private List<String> columns; // Columns involved

    @JsonProperty("predicate")
    private String predicate;     // Filter predicate (normalized form)

    @JsonProperty("estimatedRows")
    private long estimatedRows;

    @JsonProperty("costEstimate")
    private double costEstimate;

    @JsonProperty("children")
    private List<PlanOperation> children; // Child operations in the plan tree

    public PlanOperation() {}

    public PlanOperation(String type, String table, List<String> columns,
                         String predicate, long estimatedRows, double costEstimate) {
        this.type = type;
        this.table = table;
        this.columns = columns;
        this.predicate = predicate;
        this.estimatedRows = estimatedRows;
        this.costEstimate = costEstimate;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }

    public String getPredicate() { return predicate; }
    public void setPredicate(String predicate) { this.predicate = predicate; }

    public long getEstimatedRows() { return estimatedRows; }
    public void setEstimatedRows(long estimatedRows) { this.estimatedRows = estimatedRows; }

    public double getCostEstimate() { return costEstimate; }
    public void setCostEstimate(double costEstimate) { this.costEstimate = costEstimate; }

    public List<PlanOperation> getChildren() { return children; }
    public void setChildren(List<PlanOperation> children) { this.children = children; }
}

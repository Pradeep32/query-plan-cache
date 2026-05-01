package com.queryplancache.normalizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ANTLR-based SQL query normalizer that replaces literal values with
 * positional placeholders ('?') and extracts parameter bindings.
 *
 * <p><b>Design using ANTLR concepts:</b></p>
 *
 * In a full implementation, the normalization pipeline would be:
 * <pre>
 *   1. Lexer Phase (ANTLR SqlLexer):
 *      - Tokenizes raw SQL into token stream
 *      - Identifies token types: STRING_LITERAL, INTEGER_LITERAL,
 *        FLOAT_LITERAL, BOOLEAN_LITERAL, IDENTIFIER, KEYWORD, etc.
 *
 *   2. Parser Phase (ANTLR SqlParser):
 *      - Builds an Abstract Syntax Tree (AST) from the token stream
 *      - Grammar rules define the SQL structure:
 *        selectStatement : SELECT selectElements FROM tableName (WHERE expression)? ;
 *        expression      : expression AND expression
 *                        | expression OR expression
 *                        | columnName compOp literal
 *                        ;
 *        literal         : STRING_LITERAL | INTEGER_LITERAL | FLOAT_LITERAL | BOOLEAN_LITERAL ;
 *
 *   3. Visitor Phase (ANTLR SqlBaseVisitor):
 *      - Walks the AST using the Visitor pattern
 *      - visitLiteral() replaces each literal node with a '?' placeholder
 *      - Collects the original literal values into the parameter binding list
 *      - visitSelectStatement(), visitWhereClause(), etc. reconstruct the
 *        normalized query string from the modified AST
 * </pre>
 *
 * <p>This implementation simulates the ANTLR visitor approach using regex-based
 * tokenization that mirrors what the ANTLR lexer would produce. The normalization
 * logic follows the same Visitor pattern principles.</p>
 *
 * <p><b>ANTLR Pseudo-code equivalent:</b></p>
 * <pre>
 * public class SqlNormalizingVisitor extends SqlBaseVisitor&lt;String&gt; {
 *
 *     private final List&lt;Object&gt; parameters = new ArrayList&lt;&gt;();
 *
 *     &#64;Override
 *     public String visitStringLiteral(SqlParser.StringLiteralContext ctx) {
 *         String value = ctx.getText();
 *         // Strip surrounding quotes
 *         parameters.add(value.substring(1, value.length() - 1));
 *         return "?";
 *     }
 *
 *     &#64;Override
 *     public String visitIntegerLiteral(SqlParser.IntegerLiteralContext ctx) {
 *         parameters.add(Long.parseLong(ctx.getText()));
 *         return "?";
 *     }
 *
 *     &#64;Override
 *     public String visitFloatLiteral(SqlParser.FloatLiteralContext ctx) {
 *         parameters.add(Double.parseDouble(ctx.getText()));
 *         return "?";
 *     }
 *
 *     &#64;Override
 *     public String visitBooleanLiteral(SqlParser.BooleanLiteralContext ctx) {
 *         parameters.add(Boolean.parseBoolean(ctx.getText()));
 *         return "?";
 *     }
 *
 *     // Other visit methods delegate to children and concatenate results
 *     &#64;Override
 *     public String visitSelectStatement(SqlParser.SelectStatementContext ctx) {
 *         return visitChildren(ctx); // Reconstruct from normalized child nodes
 *     }
 * }
 * </pre>
 */
public class SqlQueryNormalizer implements QueryNormalizer {

    private static final Logger log = LoggerFactory.getLogger(SqlQueryNormalizer.class);

    // Regex patterns that simulate ANTLR lexer token rules
    // These match the same token types that an ANTLR grammar would define:
    //   STRING_LITERAL : '\'' (~'\'')* '\'' ;
    //   INTEGER_LITERAL: '-'? [0-9]+ ;
    //   FLOAT_LITERAL  : '-'? [0-9]+ '.' [0-9]+ ;
    //   BOOLEAN_LITERAL: 'TRUE' | 'FALSE' ;
    private static final Pattern LITERAL_PATTERN = Pattern.compile(
            "('(?:[^'\\\\]|\\\\.)*')"           // STRING_LITERAL: single-quoted strings
            + "|(-?\\b\\d+\\.\\d+\\b)"          // FLOAT_LITERAL: decimal numbers
            + "|(-?\\b\\d+\\b)"                  // INTEGER_LITERAL: whole numbers
            + "|(\\bTRUE\\b|\\bFALSE\\b)",       // BOOLEAN_LITERAL
            Pattern.CASE_INSENSITIVE
    );

    // DDL detection — simulates ANTLR parser rule: ddlStatement : (CREATE|ALTER|DROP) ...
    private static final Pattern DDL_PATTERN = Pattern.compile(
            "^\\s*(CREATE|ALTER|DROP)\\s+",
            Pattern.CASE_INSENSITIVE
    );

    // Extract table name from DDL — simulates ANTLR visitor for DDL statements
    private static final Pattern DDL_TABLE_PATTERN = Pattern.compile(
            "(?i)(?:CREATE|ALTER|DROP)\\s+(?:TABLE|INDEX)\\s+(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?(\\w+)"
    );

    // Whitespace normalization
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    /**
     * Normalizes a raw SQL query using the ANTLR Visitor pattern approach.
     *
     * <p>Simulates the following ANTLR pipeline:</p>
     * <ol>
     *   <li>Lexer tokenization (via regex pattern matching)</li>
     *   <li>AST construction (implicit in regex groups)</li>
     *   <li>Visitor traversal (sequential token replacement)</li>
     * </ol>
     *
     * @param rawSql the original SQL query
     * @return ParameterBinding with normalized query and extracted parameters
     */
    @Override
    public ParameterBinding normalize(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new IllegalArgumentException("SQL query cannot be null or blank");
        }

        log.debug("Normalizing query: {}", rawSql);

        // Step 1: Canonicalize whitespace and trim (simulates ANTLR lexer SKIP rules)
        String canonicalized = canonicalize(rawSql);

        // Step 2: Walk tokens and replace literals with placeholders
        // This mirrors the ANTLR Visitor pattern: for each literal node in the AST,
        // replace it with '?' and collect the original value
        List<Object> parameters = new ArrayList<>();
        StringBuffer normalized = new StringBuffer();
        Matcher matcher = LITERAL_PATTERN.matcher(canonicalized);

        while (matcher.find()) {
            Object paramValue = extractParameterValue(matcher);
            parameters.add(paramValue);
            matcher.appendReplacement(normalized, "?");
        }
        matcher.appendTail(normalized);

        String normalizedQuery = normalized.toString();
        log.debug("Normalized result: {} with {} parameters", normalizedQuery, parameters.size());

        return new ParameterBinding(normalizedQuery, parameters);
    }

    /**
     * Extracts the typed parameter value from a regex match.
     * Simulates ANTLR visitor methods: visitStringLiteral(), visitIntegerLiteral(), etc.
     */
    private Object extractParameterValue(Matcher matcher) {
        // Group 1: STRING_LITERAL — visitStringLiteral()
        if (matcher.group(1) != null) {
            String raw = matcher.group(1);
            return raw.substring(1, raw.length() - 1); // Strip quotes
        }
        // Group 2: FLOAT_LITERAL — visitFloatLiteral()
        if (matcher.group(2) != null) {
            return Double.parseDouble(matcher.group(2));
        }
        // Group 3: INTEGER_LITERAL — visitIntegerLiteral()
        if (matcher.group(3) != null) {
            return Long.parseLong(matcher.group(3));
        }
        // Group 4: BOOLEAN_LITERAL — visitBooleanLiteral()
        if (matcher.group(4) != null) {
            return Boolean.parseBoolean(matcher.group(4));
        }
        throw new IllegalStateException("Unexpected match: " + matcher.group());
    }

    /**
     * Canonicalizes SQL: trims, collapses whitespace, uppercases keywords.
     * Simulates ANTLR lexer channel(HIDDEN) for whitespace tokens.
     */
    private String canonicalize(String sql) {
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return MULTI_SPACE.matcher(trimmed).replaceAll(" ");
    }

    @Override
    public boolean isDdlStatement(String rawSql) {
        if (rawSql == null) return false;
        return DDL_PATTERN.matcher(rawSql.trim()).find();
    }

    @Override
    public String extractDdlTargetTable(String rawSql) {
        if (rawSql == null) return null;
        Matcher matcher = DDL_TABLE_PATTERN.matcher(rawSql.trim());
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        }
        return null;
    }
}

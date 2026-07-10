package top.szzz666.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.szzz666.database.ConnectionManager;
import top.szzz666.model.QueryResult;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static top.szzz666.config.MyConfig.maxQueryRows;
import static top.szzz666.config.MyConfig.queryTimeout;

/**
 * SQL 执行服务
 * 支持执行单条或多条 SQL，自动区分查询与更新语句
 */
public class SqlService {
    private static final Logger logger = LoggerFactory.getLogger(SqlService.class);

    private final ConnectionManager connectionManager;

    public SqlService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * 执行单条 SQL（自动判断查询/更新）
     */
    public QueryResult execute(String connId, String sql) throws SQLException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        Connection conn = connectionManager.getConnection(connId);
        long start = System.currentTimeMillis();
        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(queryTimeout);
            boolean hasResultSet = stmt.execute(sql);
            long elapsed = System.currentTimeMillis() - start;
            if (hasResultSet) {
                QueryResult result = QueryResult.queryResult(sql, elapsed);
                try (ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    for (int i = 1; i <= colCount; i++) {
                        result.getColumns().add(meta.getColumnLabel(i));
                        result.getColumnTypes().add(meta.getColumnTypeName(i));
                    }
                    int rowCount = 0;
                    while (rs.next()) {
                        if (rowCount >= maxQueryRows) {
                            result.setTruncated(true);
                            break;
                        }
                        List<Object> row = new ArrayList<>(colCount);
                        for (int i = 1; i <= colCount; i++) {
                            row.add(safeGetObject(rs, i));
                        }
                        result.getRows().add(row);
                        rowCount++;
                    }
                    result.setReturnedRows(rowCount);
                }
                return result;
            } else {
                long affected = stmt.getUpdateCount();
                QueryResult result = QueryResult.updateResult(sql, affected, elapsed);
                result.setMessage("影响行数: " + affected);
                return result;
            }
        }
    }

    /**
     * 执行多条 SQL（分号分隔），返回每条结果
     */
    public List<QueryResult> executeBatch(String connId, String sqlText) throws SQLException {
        if (sqlText == null || sqlText.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        List<String> sqls = splitSqlStatements(sqlText);
        List<QueryResult> results = new ArrayList<>();
        Connection conn = connectionManager.getConnection(connId);
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            for (String sql : sqls) {
                String trimmed = sql.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
                try {
                    results.add(execute(connId, trimmed));
                } catch (SQLException e) {
                    conn.rollback();
                    throw new SQLException("执行失败: " + trimmed + " | 错误: " + e.getMessage(), e);
                }
            }
            conn.commit();
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException ignored) {
            }
        }
        return results;
    }

    /**
     * 执行查询并返回结果（带分页）
     */
    public QueryResult executeQueryPaged(String connId, String sql, long offset, int limit) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        var dialect = connectionManager.getDialect(connId);
        if (limit > maxQueryRows) limit = maxQueryRows;
        String pagedSql = dialect.buildPagedSql(sql, offset, limit);
        long start = System.currentTimeMillis();
        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(queryTimeout);
            try (ResultSet rs = stmt.executeQuery(pagedSql)) {
                long elapsed = System.currentTimeMillis() - start;
                QueryResult result = QueryResult.queryResult(pagedSql, elapsed);
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                for (int i = 1; i <= colCount; i++) {
                    result.getColumns().add(meta.getColumnLabel(i));
                    result.getColumnTypes().add(meta.getColumnTypeName(i));
                }
                int rowCount = 0;
                while (rs.next()) {
                    if (rowCount >= limit) {
                        result.setTruncated(true);
                        break;
                    }
                    List<Object> row = new ArrayList<>(colCount);
                    for (int i = 1; i <= colCount; i++) {
                        row.add(safeGetObject(rs, i));
                    }
                    result.getRows().add(row);
                    rowCount++;
                }
                result.setReturnedRows(rowCount);
                return result;
            }
        }
    }

    /**
     * 简单的 SQL 分割（按分号，忽略字符串内的分号）
     * 不支持完整 SQL 解析，适合简单场景
     */
    private List<String> splitSqlStatements(String sqlText) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < sqlText.length(); i++) {
            char c = sqlText.charAt(i);
            char next = (i + 1 < sqlText.length()) ? sqlText.charAt(i + 1) : '\0';
            // 处理注释
            if (!inSingleQuote && !inDoubleQuote) {
                if (!inBlockComment && c == '-' && next == '-') {
                    inLineComment = true;
                }
                if (inLineComment && c == '\n') {
                    inLineComment = false;
                    current.append(c);
                    continue;
                }
                if (inLineComment) {
                    continue;
                }
                if (!inLineComment && c == '/' && next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
                if (inBlockComment && c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                    continue;
                }
                if (inBlockComment) {
                    continue;
                }
            }
            // 处理字符串
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }
            // 分号分割
            if (c == ';' && !inSingleQuote && !inDoubleQuote) {
                String s = current.toString().trim();
                if (!s.isEmpty()) statements.add(s);
                current = new StringBuilder();
                continue;
            }
            current.append(c);
        }
        String last = current.toString().trim();
        if (!last.isEmpty() && !last.startsWith("--")) {
            statements.add(last);
        }
        return statements;
    }

    /**
     * 导出查询结果为指定格式
     *
     * @param connId 连接ID
     * @param sql    查询SQL
     * @param format 格式：csv / json / sql
     * @param tableName 导出SQL INSERT时的目标表名
     * @return 导出内容字符串
     */
    public String exportData(String connId, String sql, String format, String tableName) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        var dialect = connectionManager.getDialect(connId);
        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(queryTimeout);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                List<String> columns = new ArrayList<>();
                List<String> colTypes = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                    colTypes.add(meta.getColumnTypeName(i));
                }
                // 收集所有行
                List<List<Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>(colCount);
                    for (int i = 1; i <= colCount; i++) {
                        row.add(safeGetObject(rs, i));
                    }
                    rows.add(row);
                }
                // 按格式输出
                String fmt = format == null ? "csv" : format.toLowerCase();
                switch (fmt) {
                    case "json":
                        return exportJson(columns, rows);
                    case "sql":
                        return exportSql(tableName != null ? tableName : "export_table", columns, rows, dialect);
                    case "csv":
                    default:
                        return exportCsv(columns, rows);
                }
            }
        }
    }

    /** 导出为 CSV 格式 */
    private String exportCsv(List<String> columns, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        // BOM 头（让 Excel 正确识别 UTF-8）
        sb.append('\ufeff');
        // 表头
        sb.append(String.join(",", columns.stream().map(this::csvEscape).toList())).append("\r\n");
        // 数据行
        for (List<Object> row : rows) {
            sb.append(String.join(",", row.stream().map(v -> csvEscape(v == null ? "" : v.toString())).toList())).append("\r\n");
        }
        return sb.toString();
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** 导出为 JSON 数组格式 */
    private String exportJson(List<String> columns, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int r = 0; r < rows.size(); r++) {
            sb.append("  {");
            List<Object> row = rows.get(r);
            for (int c = 0; c < columns.size(); c++) {
                if (c > 0) sb.append(", ");
                sb.append("\"").append(columns.get(c)).append("\": ");
                Object v = row.get(c);
                sb.append(jsonValue(v));
            }
            sb.append("}");
            if (r < rows.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private String jsonValue(Object v) {
        if (v == null) return "null";
        if (v instanceof Number) return v.toString();
        if (v instanceof Boolean) return v.toString();
        // 字符串转义
        String s = v.toString();
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    /** 导出为 SQL INSERT 语句 */
    private String exportSql(String tableName, List<String> columns, List<List<Object>> rows, top.szzz666.database.DatabaseDialect dialect) {
        StringBuilder sb = new StringBuilder();
        // 列名部分
        String colPart = columns.stream().map(dialect::quoteIdentifier).reduce((a, b) -> a + ", " + b).orElse("");
        for (List<Object> row : rows) {
            sb.append("INSERT INTO ").append(dialect.quoteIdentifier(tableName))
                    .append(" (").append(colPart).append(") VALUES (");
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(sqlValue(row.get(i)));
            }
            sb.append(");\n");
        }
        return sb.toString();
    }

    private String sqlValue(Object v) {
        if (v == null) return "NULL";
        if (v instanceof Number) return v.toString();
        if (v instanceof Boolean) return ((Boolean) v) ? "1" : "0";
        if (v instanceof java.sql.Date || v instanceof java.sql.Time || v instanceof java.sql.Timestamp) {
            return "'" + v.toString() + "'";
        }
        // 字符串转义
        return "'" + v.toString().replace("'", "''") + "'";
    }

    /**
     * 安全读取 ResultSet 列值，将 java.time / java.sql 时间对象转为字符串
     * 避免 Gson 反射序列化 java.time 类型时触发 Java 模块系统限制
     */
    public static Object safeGetObject(java.sql.ResultSet rs, int index) throws SQLException {
        Object value = rs.getObject(index);
        if (value == null) return null;
        // java.time 类型转为字符串
        if (value instanceof java.time.temporal.Temporal) {
            return value.toString();
        }
        // java.sql 时间类型保持原样（Gson 已注册适配器），但也转字符串更安全
        if (value instanceof java.sql.Timestamp
                || value instanceof java.sql.Date
                || value instanceof java.sql.Time) {
            return value.toString();
        }
        // byte[] 转为 Base64 字符串，避免 Gson 序列化异常
        if (value instanceof byte[]) {
            return java.util.Base64.getEncoder().encodeToString((byte[]) value);
        }
        return value;
    }
}

package top.szzz666.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.szzz666.database.ConnectionManager;
import top.szzz666.database.DatabaseDialect;
import top.szzz666.model.ColumnInfo;
import top.szzz666.model.TableInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static top.szzz666.config.MyConfig.maxQueryRows;

/**
 * 数据记录 CRUD 服务
 */
public class DataService {
    private static final Logger logger = LoggerFactory.getLogger(DataService.class);

    private final ConnectionManager connectionManager;

    public DataService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * 分页查询表数据
     *
     * @param connId    连接ID
     * @param tableName 表名
     * @param page      页码（从1开始）
     * @param pageSize  每页数量
     * @param sortColumn 排序列（可空）
     * @param sortOrder  排序方向 ASC/DESC（可空）
     * @return 查询结果：{rows, columns, columnTypes, total, page, pageSize}
     */
    public Map<String, Object> queryTableData(String connId, String tableName, int page, int pageSize,
                                              String sortColumn, String sortOrder) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);

        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > maxQueryRows) pageSize = maxQueryRows;

        long total = dialect.countRows(conn, tableName);
        long offset = (long) (page - 1) * pageSize;

        // 构建查询SQL
        String baseSql = "SELECT * FROM " + dialect.quoteIdentifier(tableName);
        if (sortColumn != null && !sortColumn.trim().isEmpty()) {
            String order = "ASC".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
            baseSql += " ORDER BY " + dialect.quoteIdentifier(sortColumn) + " " + order;
        }
        String sql = dialect.buildPagedSql(baseSql, offset, pageSize);

        List<String> columns = new ArrayList<>();
        List<String> columnTypes = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            for (int i = 1; i <= colCount; i++) {
                columns.add(meta.getColumnLabel(i));
                columnTypes.add(meta.getColumnTypeName(i));
            }
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(columns.get(i - 1), SqlService.safeGetObject(rs, i));
                }
                rows.add(row);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("columns", columns);
        result.put("columnTypes", columnTypes);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("totalPages", (long) Math.ceil((double) total / pageSize));
        return result;
    }

    /**
     * 插入记录
     */
    public int insert(String connId, String tableName, Map<String, Object> data) throws SQLException {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("插入数据不能为空");
        }
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        List<String> columns = new ArrayList<>(data.keySet());
        List<Object> values = new ArrayList<>(data.values());
        String sql = dialect.buildInsertSql(tableName, columns, "insert");
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                stmt.setObject(i + 1, values.get(i));
            }
            return stmt.executeUpdate();
        }
    }

    /**
     * 批量插入记录
     *
     * @param mode 导入模式：insert(报错) / replace(覆盖) / ignore(跳过)
     */
    public int[] batchInsert(String connId, String tableName, List<Map<String, Object>> dataList, String mode) throws SQLException {
        if (dataList == null || dataList.isEmpty()) {
            return new int[0];
        }
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        // 使用第一条数据的列作为列定义
        List<String> columns = new ArrayList<>(dataList.get(0).keySet());
        String sql = dialect.buildInsertSql(tableName, columns, mode == null ? "insert" : mode);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Map<String, Object> data : dataList) {
                for (int i = 0; i < columns.size(); i++) {
                    stmt.setObject(i + 1, data.get(columns.get(i)));
                }
                stmt.addBatch();
            }
            return stmt.executeBatch();
        }
    }

    /**
     * 更新记录
     *
     * @param connId     连接ID
     * @param tableName  表名
     * @param data       要更新的字段和值
     * @param whereClause WHERE 条件（不含WHERE关键字），为空则更新全部
     * @param whereParams WHERE 条件参数值
     */
    public int update(String connId, String tableName, Map<String, Object> data, String whereClause, List<Object> whereParams) throws SQLException {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("更新数据不能为空");
        }
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        List<String> columns = new ArrayList<>(data.keySet());
        List<Object> values = new ArrayList<>(data.values());
        String sql = dialect.buildUpdateSql(tableName, columns, whereClause);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object v : values) {
                stmt.setObject(idx++, v);
            }
            if (whereParams != null) {
                for (Object v : whereParams) {
                    stmt.setObject(idx++, v);
                }
            }
            return stmt.executeUpdate();
        }
    }

    /**
     * 删除记录
     *
     * @param connId      连接ID
     * @param tableName   表名
     * @param whereClause WHERE 条件（不含WHERE关键字），为空则删除全部
     * @param whereParams WHERE 条件参数值
     */
    public int delete(String connId, String tableName, String whereClause, List<Object> whereParams) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        String sql = dialect.buildDeleteSql(tableName, whereClause);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (whereParams != null) {
                int idx = 1;
                for (Object v : whereParams) {
                    stmt.setObject(idx++, v);
                }
            }
            return stmt.executeUpdate();
        }
    }

    /**
     * 根据主键删除记录（更安全的删除方式）
     */
    public int deleteByPk(String connId, String tableName, Map<String, Object> pkValues) throws SQLException {
        if (pkValues == null || pkValues.isEmpty()) {
            throw new IllegalArgumentException("主键值不能为空");
        }
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        TableService tableService = new TableService(connectionManager);
        TableInfo tableInfo = tableService.getTableInfo(connId, tableName);
        List<String> pks = tableInfo.getPrimaryKeys();
        if (pks.isEmpty()) {
            // 没有主键，使用传入的条件
            List<String> cols = new ArrayList<>(pkValues.keySet());
            StringBuilder where = new StringBuilder();
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) where.append(" AND ");
                where.append(dialect.quoteIdentifier(cols.get(i))).append(" = ?");
            }
            return delete(connId, tableName, where.toString(), new ArrayList<>(pkValues.values()));
        } else {
            List<String> cols = new ArrayList<>(pkValues.keySet());
            StringBuilder where = new StringBuilder();
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) where.append(" AND ");
                where.append(dialect.quoteIdentifier(cols.get(i))).append(" = ?");
            }
            return delete(connId, tableName, where.toString(), new ArrayList<>(pkValues.values()));
        }
    }

    /**
     * 根据主键更新记录
     */
    public int updateByPk(String connId, String tableName, Map<String, Object> data, Map<String, Object> pkValues) throws SQLException {
        if (data == null || data.isEmpty()) throw new IllegalArgumentException("更新数据不能为空");
        if (pkValues == null || pkValues.isEmpty()) throw new IllegalArgumentException("主键值不能为空");
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        // 过滤掉主键字段，只更新非主键字段
        Map<String, Object> updateData = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (!pkValues.containsKey(e.getKey())) {
                updateData.put(e.getKey(), e.getValue());
            }
        }
        if (updateData.isEmpty()) {
            throw new IllegalArgumentException("没有可更新的非主键字段");
        }
        List<String> pkCols = new ArrayList<>(pkValues.keySet());
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < pkCols.size(); i++) {
            if (i > 0) where.append(" AND ");
            where.append(dialect.quoteIdentifier(pkCols.get(i))).append(" = ?");
        }
        return update(connId, tableName, updateData, where.toString(), new ArrayList<>(pkValues.values()));
    }

    /**
     * 导入数据到指定表
     *
     * @param connId    连接ID
     * @param tableName 表名
     * @param format    格式：csv / json
     * @param content   数据内容
     * @return 导入结果统计
     */
    public Map<String, Object> importData(String connId, String tableName, String format, String content, String mode) throws SQLException {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("导入数据不能为空");
        }
        String fmt = format == null ? "csv" : format.toLowerCase();
        List<Map<String, Object>> rows;
        switch (fmt) {
            case "json":
                rows = parseJsonData(content);
                break;
            case "csv":
            default:
                rows = parseCsvData(content);
                break;
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("解析后没有有效数据行");
        }

        // 批量插入
        int[] results = batchInsert(connId, tableName, rows, mode);
        int success = 0;
        for (int r : results) {
            if (r >= 0) success += r;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRows", rows.size());
        result.put("affectedRows", success);
        result.put("format", fmt);
        result.put("table", tableName);
        return result;
    }

    /**
     * 解析 CSV 数据（第一行为列名）
     */
    private List<Map<String, Object>> parseCsvData(String content) {
        List<Map<String, Object>> rows = new ArrayList<>();
        // 去除 BOM 头
        if (content.startsWith("\ufeff")) {
            content = content.substring(1);
        }
        String[] lines = content.split("\r?\n");
        if (lines.length < 2) return rows;

        // 解析表头
        List<String> headers = parseCsvLine(lines[0]);
        if (headers.isEmpty()) return rows;

        // 解析数据行
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            List<String> values = parseCsvLine(line);
            Map<String, Object> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                String val = j < values.size() ? values.get(j) : "";
                // 空字符串视为 null
                row.put(headers.get(j), val.isEmpty() ? null : val);
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 解析单行 CSV（支持引号包裹和逗号转义）
     */
    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    result.add(current.toString().trim());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
        }
        result.add(current.toString().trim());
        return result;
    }

    /**
     * 解析 JSON 数据（数组格式，每个对象的 key 为列名）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonData(String content) {
        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>() {}.getType();
        List<Map<String, Object>> rows = top.szzz666.tools.JsonUtil.fromJson(content, type);
        if (rows == null) return new ArrayList<>();
        // 确保所有值不为 undefined
        for (Map<String, Object> row : rows) {
            row.entrySet().removeIf(e -> e.getValue() == null);
        }
        return rows;
    }
}

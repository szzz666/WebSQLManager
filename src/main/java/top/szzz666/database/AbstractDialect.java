package top.szzz666.database;

import top.szzz666.model.ColumnDefinition;
import top.szzz666.model.ColumnInfo;
import top.szzz666.model.IndexInfo;
import top.szzz666.model.TableInfo;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 方言抽象基类，提供通用 JDBC 元数据实现
 * 子类可覆写特定方法以实现方言差异
 */
public abstract class AbstractDialect implements DatabaseDialect {

    @Override
    public List<TableInfo> listTables(Connection conn) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = null;
        String schema = null;
        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                TableInfo t = new TableInfo();
                t.setName(rs.getString("TABLE_NAME"));
                t.setTableType(rs.getString("TABLE_TYPE"));
                t.setComment(rs.getString("REMARKS"));
                tables.add(t);
            }
        }
        return tables;
    }

    @Override
    public TableInfo getTableInfo(Connection conn, String tableName) throws SQLException {
        TableInfo table = new TableInfo();
        table.setName(tableName);

        DatabaseMetaData meta = conn.getMetaData();
        String catalog = null;
        String schema = null;

        // 表基本信息
        try (ResultSet rs = meta.getTables(catalog, schema, tableName, new String[]{"TABLE", "VIEW"})) {
            if (rs.next()) {
                table.setTableType(rs.getString("TABLE_TYPE"));
                table.setComment(rs.getString("REMARKS"));
            }
        }

        // 列信息
        List<ColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
            while (rs.next()) {
                ColumnInfo col = new ColumnInfo();
                col.setName(rs.getString("COLUMN_NAME"));
                col.setType(rs.getString("TYPE_NAME"));
                col.setLength(rs.getInt("COLUMN_SIZE"));
                col.setScale(rs.getInt("DECIMAL_DIGITS"));
                col.setNullable(DatabaseMetaData.columnNullable == rs.getInt("NULLABLE"));
                col.setDefaultValue(rs.getString("COLUMN_DEF"));
                col.setComment(rs.getString("REMARKS"));
                col.setOrdinal(rs.getInt("ORDINAL_POSITION"));
                String isAuto = rs.getString("IS_AUTOINCREMENT");
                col.setAutoIncrement("YES".equalsIgnoreCase(isAuto));
                columns.add(col);
            }
        }
        table.setColumns(columns);

        // 主键
        List<String> pks = new ArrayList<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                pks.add(colName);
                ColumnInfo col = table.getColumn(colName);
                if (col != null) col.setPrimaryKey(true);
            }
        }
        table.setPrimaryKeys(pks);

        return table;
    }

    @Override
    public List<IndexInfo> listIndexes(Connection conn, String tableName) throws SQLException {
        List<IndexInfo> indexes = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getIndexInfo(null, null, tableName, false, false)) {
            while (rs.next()) {
                IndexInfo idx = new IndexInfo();
                idx.setName(rs.getString("INDEX_NAME"));
                idx.setColumnName(rs.getString("COLUMN_NAME"));
                idx.setUnique(!rs.getBoolean("NON_UNIQUE"));
                int type = rs.getInt("TYPE");
                idx.setPrimaryKey(type == DatabaseMetaData.tableIndexClustered);
                idx.setAscOrDesc(rs.getString("ASC_OR_DESC"));
                idx.setOrdinal(rs.getInt("ORDINAL_POSITION"));
                indexes.add(idx);
            }
        }
        return indexes;
    }

    @Override
    public long countRows(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + quoteIdentifier(tableName);
        try (var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    @Override
    public String buildDropTableSql(String tableName) {
        return "DROP TABLE IF EXISTS " + quoteIdentifier(tableName);
    }

    @Override
    public String buildTruncateTableSql(String tableName) {
        return "DELETE FROM " + quoteIdentifier(tableName);
    }

    @Override
    public String buildRenameTableSql(String oldName, String newName) {
        return "ALTER TABLE " + quoteIdentifier(oldName) + " RENAME TO " + quoteIdentifier(newName);
    }

    @Override
    public String buildCreateTableSql(String tableName, List<ColumnDefinition> columns, String tableComment) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(quoteIdentifier(tableName)).append(" (\n");
        List<String> pkCols = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ColumnDefinition col = columns.get(i);
            sb.append("  ").append(buildColumnDefSql(col));
            if (i < columns.size() - 1) sb.append(",");
            sb.append("\n");
            if (col.isPrimaryKey()) pkCols.add(col.getName());
        }
        if (!pkCols.isEmpty()) {
            sb.append("  ,PRIMARY KEY (");
            for (int i = 0; i < pkCols.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(quoteIdentifier(pkCols.get(i)));
            }
            sb.append(")\n");
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String buildColumnDefSql(ColumnDefinition col) {
        StringBuilder sb = new StringBuilder();
        sb.append(quoteIdentifier(col.getName())).append(" ");
        sb.append(col.getType() == null ? "VARCHAR" : col.getType().toUpperCase());
        if (col.getLength() > 0) {
            sb.append("(").append(col.getLength());
            if (col.getScale() > 0) sb.append(",").append(col.getScale());
            sb.append(")");
        }
        if (!col.isNullable()) sb.append(" NOT NULL");
        if (col.isAutoIncrement()) sb.append(" AUTOINCREMENT");
        if (col.isUnique()) sb.append(" UNIQUE");
        if (col.getDefaultValue() != null && !col.getDefaultValue().isEmpty()) {
            sb.append(" DEFAULT ").append(col.getDefaultValue());
        }
        return sb.toString();
    }

    @Override
    public String buildAddColumnSql(String tableName, ColumnDefinition col) {
        return "ALTER TABLE " + quoteIdentifier(tableName) + " ADD COLUMN " + buildColumnDefSql(col);
    }

    @Override
    public String buildDropColumnSql(String tableName, String columnName) {
        return "ALTER TABLE " + quoteIdentifier(tableName) + " DROP COLUMN " + quoteIdentifier(columnName);
    }

    @Override
    public String buildInsertSql(String tableName, List<String> columns, String mode) {
        String prefix = "INSERT INTO";
        if ("replace".equalsIgnoreCase(mode)) {
            prefix = "INSERT OR REPLACE INTO";
        } else if ("ignore".equalsIgnoreCase(mode)) {
            prefix = "INSERT OR IGNORE INTO";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append(" ").append(quoteIdentifier(tableName)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(quoteIdentifier(columns.get(i)));
        }
        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String buildUpdateSql(String tableName, List<String> columns, String whereClause) {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ").append(quoteIdentifier(tableName)).append(" SET ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(quoteIdentifier(columns.get(i))).append(" = ?");
        }
        if (whereClause != null && !whereClause.trim().isEmpty()) {
            sb.append(" WHERE ").append(whereClause);
        }
        return sb.toString();
    }

    @Override
    public String buildDeleteSql(String tableName, String whereClause) {
        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ").append(quoteIdentifier(tableName));
        if (whereClause != null && !whereClause.trim().isEmpty()) {
            sb.append(" WHERE ").append(whereClause);
        }
        return sb.toString();
    }

    @Override
    public boolean supportsTruncate() {
        return true;
    }

    @Override
    public String getDatabaseVersion(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        return meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
    }
}

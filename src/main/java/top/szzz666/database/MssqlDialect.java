package top.szzz666.database;

import top.szzz666.model.ColumnDefinition;

/**
 * SQL Server (MSSQL) 方言实现
 */
public class MssqlDialect extends AbstractDialect {

    @Override
    public String name() {
        return "mssql";
    }

    @Override
    public String driverClassName() {
        return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    }

    @Override
    public String quoteIdentifier(String name) {
        if (name == null) return "";
        return "[" + name.replace("]", "]]") + "]";
    }

    @Override
    public String buildPagedSql(String sql, long offset, int limit) {
        String upperSql = sql.toUpperCase().trim();
        if (!upperSql.contains("ORDER BY")) {
            sql = sql + " ORDER BY 1";
        }
        if (offset <= 0) {
            return sql + " OFFSET 0 ROWS FETCH NEXT " + limit + " ROWS ONLY";
        }
        return sql + " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
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
        if (col.isAutoIncrement()) sb.append(" IDENTITY(1,1)");
        if (!col.isNullable()) sb.append(" NOT NULL");
        if (col.isUnique()) sb.append(" UNIQUE");
        if (col.getDefaultValue() != null && !col.getDefaultValue().isEmpty()) {
            sb.append(" DEFAULT ").append(col.getDefaultValue());
        }
        return sb.toString();
    }

    @Override
    public String buildModifyColumnSql(String tableName, ColumnDefinition col) {
        return "ALTER TABLE " + quoteIdentifier(tableName) +
                " ALTER COLUMN " + buildColumnDefSql(col);
    }

    @Override
    public String buildTruncateTableSql(String tableName) {
        return "TRUNCATE TABLE " + quoteIdentifier(tableName);
    }

    @Override
    public boolean supportsMultiColumnAlter() {
        return false;
    }
}

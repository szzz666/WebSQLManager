package top.szzz666.database;

import top.szzz666.model.ColumnDefinition;

/**
 * H2 数据库方言实现
 */
public class H2Dialect extends AbstractDialect {

    @Override
    public String name() {
        return "h2";
    }

    @Override
    public String driverClassName() {
        return "org.h2.Driver";
    }

    @Override
    public String quoteIdentifier(String name) {
        if (name == null) return "";
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String buildPagedSql(String sql, long offset, int limit) {
        if (offset <= 0) {
            return sql + " LIMIT " + limit;
        }
        return sql + " LIMIT " + limit + " OFFSET " + offset;
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
        if (col.isAutoIncrement()) sb.append(" AUTO_INCREMENT");
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
}

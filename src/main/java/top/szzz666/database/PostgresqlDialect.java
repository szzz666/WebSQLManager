package top.szzz666.database;

import top.szzz666.model.ColumnDefinition;

/**
 * PostgreSQL 方言实现
 */
public class PostgresqlDialect extends AbstractDialect {

    @Override
    public String name() {
        return "postgresql";
    }

    @Override
    public String driverClassName() {
        return "org.postgresql.Driver";
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
        if (col.isAutoIncrement()) {
            String type = col.getType() == null ? "" : col.getType().toUpperCase();
            if (type.contains("BIG")) {
                sb.append("BIGSERIAL");
            } else {
                sb.append("SERIAL");
            }
        } else {
            sb.append(col.getType() == null ? "VARCHAR" : col.getType().toUpperCase());
            if (col.getLength() > 0) {
                sb.append("(").append(col.getLength());
                if (col.getScale() > 0) sb.append(",").append(col.getScale());
                sb.append(")");
            }
        }
        if (!col.isNullable()) sb.append(" NOT NULL");
        if (col.isUnique()) sb.append(" UNIQUE");
        if (col.getDefaultValue() != null && !col.getDefaultValue().isEmpty()) {
            sb.append(" DEFAULT ").append(col.getDefaultValue());
        }
        return sb.toString();
    }

    @Override
    public String buildModifyColumnSql(String tableName, ColumnDefinition col) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ").append(quoteIdentifier(tableName));
        sb.append(" ALTER COLUMN ").append(quoteIdentifier(col.getName()));
        sb.append(" TYPE ").append(col.getType() == null ? "VARCHAR" : col.getType().toUpperCase());
        if (col.getLength() > 0) {
            sb.append("(").append(col.getLength());
            if (col.getScale() > 0) sb.append(",").append(col.getScale());
            sb.append(")");
        }
        if (!col.isNullable()) {
            sb.append("; ALTER TABLE ").append(quoteIdentifier(tableName));
            sb.append(" ALTER COLUMN ").append(quoteIdentifier(col.getName()));
            sb.append(" SET NOT NULL");
        } else {
            sb.append("; ALTER TABLE ").append(quoteIdentifier(tableName));
            sb.append(" ALTER COLUMN ").append(quoteIdentifier(col.getName()));
            sb.append(" DROP NOT NULL");
        }
        return sb.toString();
    }

    @Override
    public boolean supportsMultiColumnAlter() {
        return false;
    }
}

package top.szzz666.database;

import top.szzz666.model.ColumnDefinition;

/**
 * Oracle 方言实现
 */
public class OracleDialect extends AbstractDialect {

    @Override
    public String name() {
        return "oracle";
    }

    @Override
    public String driverClassName() {
        return "oracle.jdbc.OracleDriver";
    }

    @Override
    public String quoteIdentifier(String name) {
        if (name == null) return "";
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String buildPagedSql(String sql, long offset, int limit) {
        if (offset <= 0) {
            return sql + " FETCH FIRST " + limit + " ROWS ONLY";
        }
        return sql + " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }

    @Override
    public String buildColumnDefSql(ColumnDefinition col) {
        StringBuilder sb = new StringBuilder();
        sb.append(quoteIdentifier(col.getName())).append(" ");
        sb.append(col.getType() == null ? "VARCHAR2" : col.getType().toUpperCase());
        if (col.getLength() > 0) {
            sb.append("(").append(col.getLength());
            if (col.getScale() > 0) sb.append(",").append(col.getScale());
            sb.append(")");
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
        return "ALTER TABLE " + quoteIdentifier(tableName) +
                " MODIFY " + buildColumnDefSql(col);
    }

    @Override
    public String buildRenameColumnSql(String tableName, String oldName, String newName, ColumnDefinition col) {
        return "ALTER TABLE " + quoteIdentifier(tableName) +
                " RENAME COLUMN " + quoteIdentifier(oldName) +
                " TO " + quoteIdentifier(newName);
    }

    @Override
    public boolean supportsMultiColumnAlter() {
        return false;
    }
}

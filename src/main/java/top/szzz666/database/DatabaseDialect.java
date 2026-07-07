package top.szzz666.database;

import top.szzz666.model.ColumnDefinition;
import top.szzz666.model.ColumnInfo;
import top.szzz666.model.IndexInfo;
import top.szzz666.model.TableInfo;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 数据库方言抽象接口
 * 用于屏蔽 SQLite / MySQL 之间的 SQL 差异
 */
public interface DatabaseDialect {

    /**
     * 方言标识
     */
    String name();

    /**
     * 方言驱动类名
     */
    String driverClassName();

    /**
     * 获取所有表名（含视图）
     */
    List<TableInfo> listTables(Connection conn) throws SQLException;

    /**
     * 获取表的详细结构（列、主键、索引、注释等）
     */
    TableInfo getTableInfo(Connection conn, String tableName) throws SQLException;

    /**
     * 获取表的索引列表
     */
    List<IndexInfo> listIndexes(Connection conn, String tableName) throws SQLException;

    /**
     * 获取表的行数
     */
    long countRows(Connection conn, String tableName) throws SQLException;

    /**
     * 生成建表 SQL
     */
    String buildCreateTableSql(String tableName, List<ColumnDefinition> columns, String tableComment);

    /**
     * 生成删表 SQL
     */
    String buildDropTableSql(String tableName);

    /**
     * 生成重命名表 SQL
     */
    String buildRenameTableSql(String oldName, String newName);

    /**
     * 生成清空表 SQL
     */
    String buildTruncateTableSql(String tableName);

    /**
     * 生成分页查询 SQL
     *
     * @param sql     原始查询 SQL
     * @param offset  偏移量
     * @param limit   每页数量
     */
    String buildPagedSql(String sql, long offset, int limit);

    /**
     * 生成列定义片段 SQL（用于建表/改表）
     */
    String buildColumnDefSql(ColumnDefinition col);

    /**
     * 生成添加列 SQL
     */
    String buildAddColumnSql(String tableName, ColumnDefinition col);

    /**
     * 生成修改列 SQL
     */
    String buildModifyColumnSql(String tableName, ColumnDefinition col);

    /**
     * 生成删除列 SQL
     */
    String buildDropColumnSql(String tableName, String columnName);

    /**
     * 生成重命名列 SQL
     */
    String buildRenameColumnSql(String tableName, String oldName, String newName, ColumnDefinition col);

    /**
     * 生成 INSERT 语句
     *
     * @param tableName 表名
     * @param columns   列名列表
     * @param mode      导入模式：insert(报错) / replace(覆盖) / ignore(跳过)
     */
    String buildInsertSql(String tableName, List<String> columns, String mode);

    /**
     * 生成 UPDATE 语句
     *
     * @param tableName   表名
     * @param columns     要更新的列
     * @param values      对应值
     * @param whereClause WHERE 条件（不含 WHERE 关键字）
     */
    String buildUpdateSql(String tableName, List<String> columns, String whereClause);

    /**
     * 生成 DELETE 语句
     */
    String buildDeleteSql(String tableName, String whereClause);

    /**
     * 标识符引用符（MySQL 为 `，SQLite 为 "）
     */
    String quoteIdentifier(String name);

    /**
     * 是否支持单条 ALTER TABLE 修改多列
     */
    boolean supportsMultiColumnAlter();

    /**
     * 是否支持 TRUNCATE 语句
     */
    boolean supportsTruncate();

    /**
     * 获取数据库版本信息
     */
    String getDatabaseVersion(Connection conn) throws SQLException;
}

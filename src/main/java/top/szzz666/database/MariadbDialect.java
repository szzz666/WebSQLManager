package top.szzz666.database;

/**
 * MariaDB 方言实现（兼容 MySQL 语法）
 */
public class MariadbDialect extends MysqlDialect {

    @Override
    public String name() {
        return "mariadb";
    }

    @Override
    public String driverClassName() {
        return "org.mariadb.jdbc.Driver";
    }
}

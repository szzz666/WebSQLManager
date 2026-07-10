package top.szzz666.database;

import top.szzz666.model.ConnectionConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方言工厂，根据连接类型获取对应方言
 */
public class DialectFactory {

    private static final Map<String, DatabaseDialect> DIALECTS = new ConcurrentHashMap<>();

    static {
        register(new SqliteDialect());
        register(new MysqlDialect());
        register(new MariadbDialect());
        register(new PostgresqlDialect());
        register(new MssqlDialect());
        register(new OracleDialect());
        register(new H2Dialect());
    }

    public static void register(DatabaseDialect dialect) {
        DIALECTS.put(dialect.name().toLowerCase(), dialect);
    }

    public static DatabaseDialect get(String type) {
        if (type == null) return null;
        DatabaseDialect dialect = DIALECTS.get(type.toLowerCase());
        if (dialect == null) {
            throw new IllegalArgumentException("不支持的数据库类型: " + type);
        }
        return dialect;
    }

    /**
     * 根据连接配置获取方言
     */
    public static DatabaseDialect get(ConnectionConfig config) {
        String type = config.getType();
        if (type == null || type.isEmpty()) {
            type = ConnectionConfig.detectType(config.getJdbcUrl());
        }
        return get(type);
    }

    /**
     * 列出所有已注册方言类型
     */
    public static java.util.Set<String> listTypes() {
        return DIALECTS.keySet();
    }
}

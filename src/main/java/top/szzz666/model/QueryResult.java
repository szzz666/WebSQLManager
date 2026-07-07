package top.szzz666.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 执行结果
 */
@Data
public class QueryResult {
    /** 是否为查询语句（有结果集） */
    private boolean query;
    /** 列名 */
    private List<String> columns = new ArrayList<>();
    /** 列类型 */
    private List<String> columnTypes = new ArrayList<>();
    /** 数据行 */
    private List<List<Object>> rows = new ArrayList<>();
    /** 影响行数（非查询语句） */
    private long affectedRows;
    /** 执行耗时（毫秒） */
    private long elapsed;
    /** SQL语句 */
    private String sql;
    /** 是否被截断（超过最大行数） */
    private boolean truncated;
    /** 实际返回行数 */
    private int returnedRows;
    /** 提示消息 */
    private String message;

    public static QueryResult queryResult(String sql, long elapsed) {
        QueryResult r = new QueryResult();
        r.query = true;
        r.sql = sql;
        r.elapsed = elapsed;
        return r;
    }

    public static QueryResult updateResult(String sql, long affected, long elapsed) {
        QueryResult r = new QueryResult();
        r.query = false;
        r.sql = sql;
        r.affectedRows = affected;
        r.elapsed = elapsed;
        return r;
    }
}

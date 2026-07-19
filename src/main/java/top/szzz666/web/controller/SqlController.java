package top.szzz666.web.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.szzz666.model.ApiResponse;
import top.szzz666.service.SqlService;
import top.szzz666.tools.JsonUtil;

import java.math.BigDecimal;
import java.util.Map;

import static spark.Spark.*;

/**
 * SQL 执行控制器
 * 路由前缀: /api/connections/:connId/sql
 */
public class SqlController {
    private static final Logger logger = LoggerFactory.getLogger(SqlController.class);

    private final SqlService sqlService;

    public SqlController(SqlService sqlService) {
        this.sqlService = sqlService;
    }

    public void registerRoutes() {
        // 执行单条 SQL
        post("/api/connections/:connId/sql/execute", (req, res) -> {
            String connId = req.params(":connId");
            Map<String, Object> body = JsonUtil.toMap(req.body());
            String sql = String.valueOf(body.get("sql"));
            var result = sqlService.execute(connId, sql);
            logger.info("执行SQL (连接:{}): {} | 耗时:{}ms", connId, sql.length() > 100 ? sql.substring(0, 100) + "..." : sql, result.getElapsed());
            return JsonUtil.toCompactJson(ApiResponse.success(result));
        });

        // 批量执行多条 SQL
        post("/api/connections/:connId/sql/batch", (req, res) -> {
            String connId = req.params(":connId");
            Map<String, Object> body = JsonUtil.toMap(req.body());
            String sql = String.valueOf(body.get("sql"));
            var results = sqlService.executeBatch(connId, sql);
            return JsonUtil.toCompactJson(ApiResponse.success(results, "执行完成: " + results.size() + " 条语句"));
        });

        // 分页执行查询
        post("/api/connections/:connId/sql/query", (req, res) -> {
            String connId = req.params(":connId");
            Map<String, Object> body = JsonUtil.toMap(req.body());
            String sql = String.valueOf(body.get("sql"));
            long offset = body.get("offset") == null ? 0 : toLong(body.get("offset"), "offset");
            int limit = body.get("limit") == null ? 100 : toInt(body.get("limit"), "limit");
            var result = sqlService.executeQueryPaged(connId, sql, offset, limit);
            return JsonUtil.toCompactJson(ApiResponse.success(result));
        });

        // 导出查询结果（CSV/JSON/SQL）
        post("/api/connections/:connId/sql/export", (req, res) -> {
            String connId = req.params(":connId");
            Map<String, Object> body = JsonUtil.toMap(req.body());
            String sql = String.valueOf(body.get("sql"));
            String format = body.get("format") == null ? "csv" : String.valueOf(body.get("format"));
            String tableName = body.get("tableName") == null ? null : String.valueOf(body.get("tableName"));
            String content = sqlService.exportData(connId, sql, format, tableName);
            // 设置下载响应头
            String ext = "csv".equals(format) ? "csv" : "json".equals(format) ? "json" : "sql";
            String filename = body.get("filename") == null ? "export_" + System.currentTimeMillis() + "." + ext
                    : String.valueOf(body.get("filename"));
            res.type("text/plain;charset=utf-8");
            res.header("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            return content;
        });
    }

    private static long toLong(Object value, String field) {
        try {
            return new BigDecimal(String.valueOf(value)).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException(field + " 必须是整数", e);
        }
    }

    private static int toInt(Object value, String field) {
        try {
            return new BigDecimal(String.valueOf(value)).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException(field + " 必须是整数", e);
        }
    }
}

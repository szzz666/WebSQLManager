package top.szzz666.web.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.szzz666.model.ApiResponse;
import top.szzz666.model.ColumnDefinition;
import top.szzz666.service.ConnectionService;
import top.szzz666.service.TableService;
import top.szzz666.tools.JsonUtil;

import java.util.List;
import java.util.Map;

import static spark.Spark.*;

/**
 * 表结构管理控制器
 * 路由前缀: /api/connections/:connId/tables
 */
public class TableController {
    private static final Logger logger = LoggerFactory.getLogger(TableController.class);

    private final TableService tableService;
    private final ConnectionService connectionService;

    public TableController(TableService tableService, ConnectionService connectionService) {
        this.tableService = tableService;
        this.connectionService = connectionService;
    }

    public void registerRoutes() {
        // 列出所有表
        get("/api/connections/:connId/tables", (req, res) -> {
            String connId = req.params(":connId");
            checkConnection(connId);
            return JsonUtil.toCompactJson(ApiResponse.success(tableService.listTables(connId)));
        });

        // 获取表详情
        get("/api/connections/:connId/tables/:table", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            checkConnection(connId);
            return JsonUtil.toCompactJson(ApiResponse.success(tableService.getTableInfo(connId, table)));
        });

        // 获取表索引
        get("/api/connections/:connId/tables/:table/indexes", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            return JsonUtil.toCompactJson(ApiResponse.success(tableService.listIndexes(connId, table)));
        });

        // 创建表
        post("/api/connections/:connId/tables", (req, res) -> {
            String connId = req.params(":connId");
            checkConnection(connId);
            Map<String, Object> body = JsonUtil.toMap(req.body());
            String tableName = String.valueOf(body.get("name"));
            String comment = body.get("comment") == null ? null : String.valueOf(body.get("comment"));
            @SuppressWarnings("unchecked")
            List<ColumnDefinition> columns = JsonUtil.fromJson(
                    JsonUtil.toCompactJson(body.get("columns")),
                    new com.google.gson.reflect.TypeToken<List<ColumnDefinition>>() {}.getType());
            tableService.createTable(connId, tableName, columns, comment);
            logger.info("创建表: {} (连接: {})", tableName, connId);
            return JsonUtil.toCompactJson(ApiResponse.success(null, "表创建成功"));
        });

        // 删除表
        delete("/api/connections/:connId/tables/:table", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            tableService.dropTable(connId, table);
            logger.info("删除表: {} (连接: {})", table, connId);
            return JsonUtil.toCompactJson(ApiResponse.success(null, "表已删除"));
        });

        // 重命名表
        post("/api/connections/:connId/tables/:table/rename", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            Map<String, Object> body = JsonUtil.toMap(req.body());
            String newName = String.valueOf(body.get("newName"));
            tableService.renameTable(connId, table, newName);
            return JsonUtil.toCompactJson(ApiResponse.success(null, "表已重命名"));
        });

        // 清空表
        post("/api/connections/:connId/tables/:table/truncate", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            tableService.truncateTable(connId, table);
            return JsonUtil.toCompactJson(ApiResponse.success(null, "表已清空"));
        });

        // 修改表结构（批量）
        post("/api/connections/:connId/tables/:table/alter", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            @SuppressWarnings("unchecked")
            List<ColumnDefinition> changes = JsonUtil.fromJson(
                    JsonUtil.toCompactJson(JsonUtil.toMap(req.body()).get("columns")),
                    new com.google.gson.reflect.TypeToken<List<ColumnDefinition>>() {}.getType());
            List<String> executed = tableService.alterTable(connId, table, changes);
            return JsonUtil.toCompactJson(ApiResponse.success(executed, "表结构已修改"));
        });

        // 添加单列
        post("/api/connections/:connId/tables/:table/columns", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            ColumnDefinition col = JsonUtil.fromJson(req.body(), ColumnDefinition.class);
            tableService.addColumn(connId, table, col);
            return JsonUtil.toCompactJson(ApiResponse.success(null, "列已添加"));
        });

        // 修改单列
        put("/api/connections/:connId/tables/:table/columns/:column", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            ColumnDefinition col = JsonUtil.fromJson(req.body(), ColumnDefinition.class);
            tableService.modifyColumn(connId, table, col);
            return JsonUtil.toCompactJson(ApiResponse.success(null, "列已修改"));
        });

        // 删除单列
        delete("/api/connections/:connId/tables/:table/columns/:column", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            String column = req.params(":column");
            tableService.dropColumn(connId, table, column);
            return JsonUtil.toCompactJson(ApiResponse.success(null, "列已删除"));
        });
    }

    private void checkConnection(String connId) {
        if (connectionService.getConnection(connId) == null) {
            throw new IllegalArgumentException("连接不存在或已被删除: " + connId);
        }
    }
}

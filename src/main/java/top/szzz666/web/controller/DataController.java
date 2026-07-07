package top.szzz666.web.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.szzz666.model.ApiResponse;
import top.szzz666.service.DataService;
import top.szzz666.service.TableService;
import top.szzz666.tools.JsonUtil;

import java.util.List;
import java.util.Map;

import static spark.Spark.*;

/**
 * 数据记录 CRUD 控制器
 * 路由前缀: /api/connections/:connId/data
 */
public class DataController {
    private static final Logger logger = LoggerFactory.getLogger(DataController.class);

    private final DataService dataService;
    private final TableService tableService;

    public DataController(DataService dataService, TableService tableService) {
        this.dataService = dataService;
        this.tableService = tableService;
    }

    @SuppressWarnings("unchecked")
    public void registerRoutes() {
        // 分页查询表数据
        get("/api/connections/:connId/data/:table", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            int page = req.queryParams("page") != null ? Integer.parseInt(req.queryParams("page")) : 1;
            int pageSize = req.queryParams("pageSize") != null ? Integer.parseInt(req.queryParams("pageSize")) : 20;
            String sortColumn = req.queryParams("sortColumn");
            String sortOrder = req.queryParams("sortOrder");
            Map<String, Object> result = dataService.queryTableData(connId, table, page, pageSize, sortColumn, sortOrder);
            return JsonUtil.toCompactJson(ApiResponse.success(result));
        });

        // 插入记录
        post("/api/connections/:connId/data/:table", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            Map<String, Object> data = JsonUtil.toMap(req.body());
            int affected = dataService.insert(connId, table, data);
            return JsonUtil.toCompactJson(ApiResponse.success(Map.of("affected", affected), "插入成功"));
        });

        // 批量插入
        post("/api/connections/:connId/data/:table/batch", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            List<Map<String, Object>> list = JsonUtil.fromJson(
                    JsonUtil.toCompactJson(JsonUtil.toMap(req.body()).get("rows")),
                    new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>() {}.getType());
            int[] result = dataService.batchInsert(connId, table, list, "insert");
            int total = 0;
            for (int r : result) total += r;
            return JsonUtil.toCompactJson(ApiResponse.success(
                    Map.of("affected", total, "batchSize", result.length), "批量插入完成"));
        });

        // 更新记录
        put("/api/connections/:connId/data/:table", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            Map<String, Object> body = JsonUtil.toMap(req.body());
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            String where = body.get("where") == null ? null : String.valueOf(body.get("where"));
            List<Object> params = body.get("params") == null ? null :
                    JsonUtil.fromJson(JsonUtil.toCompactJson(body.get("params")),
                            new com.google.gson.reflect.TypeToken<List<Object>>() {}.getType());
            int affected = dataService.update(connId, table, data, where, params);
            return JsonUtil.toCompactJson(ApiResponse.success(Map.of("affected", affected), "更新成功"));
        });

        // 删除记录
        delete("/api/connections/:connId/data/:table", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            Map<String, Object> body = JsonUtil.toMap(req.body());
            String where = body.get("where") == null ? null : String.valueOf(body.get("where"));
            List<Object> params = body.get("params") == null ? null :
                    JsonUtil.fromJson(JsonUtil.toCompactJson(body.get("params")),
                            new com.google.gson.reflect.TypeToken<List<Object>>() {}.getType());
            int affected = dataService.delete(connId, table, where, params);
            return JsonUtil.toCompactJson(ApiResponse.success(Map.of("affected", affected), "删除成功"));
        });

        // 按主键更新
        put("/api/connections/:connId/data/:table/pk", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            Map<String, Object> body = JsonUtil.toMap(req.body());
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            Map<String, Object> pk = (Map<String, Object>) body.get("primaryKey");
            int affected = dataService.updateByPk(connId, table, data, pk);
            return JsonUtil.toCompactJson(ApiResponse.success(Map.of("affected", affected), "更新成功"));
        });

        // 按主键删除
        post("/api/connections/:connId/data/:table/pk/delete", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            Map<String, Object> pk = JsonUtil.toMap(req.body());
            int affected = dataService.deleteByPk(connId, table, pk);
            return JsonUtil.toCompactJson(ApiResponse.success(Map.of("affected", affected), "删除成功"));
        });

        // 导入数据（CSV/JSON）
        post("/api/connections/:connId/data/:table/import", (req, res) -> {
            String connId = req.params(":connId");
            String table = req.params(":table");
            Map<String, Object> body = JsonUtil.toMap(req.body());
            String format = body.get("format") == null ? "csv" : String.valueOf(body.get("format"));
            String content = body.get("content") == null ? "" : String.valueOf(body.get("content"));
            String mode = body.get("mode") == null ? "insert" : String.valueOf(body.get("mode"));
            Map<String, Object> result = dataService.importData(connId, table, format, content, mode);
            return JsonUtil.toCompactJson(ApiResponse.success(result,
                    "导入完成: 共 " + result.get("totalRows") + " 行，影响 " + result.get("affectedRows") + " 行"));
        });
    }
}

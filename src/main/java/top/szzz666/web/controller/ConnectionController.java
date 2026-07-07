package top.szzz666.web.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.szzz666.model.ApiResponse;
import top.szzz666.model.ConnectionConfig;
import top.szzz666.service.ConnectionService;
import top.szzz666.tools.JsonUtil;

import java.util.List;
import java.util.Map;

import static spark.Spark.*;

/**
 * 数据库连接管理控制器
 * 路由前缀: /api/connections
 */
public class ConnectionController {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionController.class);

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    public void registerRoutes() {
        // 列出所有连接
        get("/api/connections", (req, res) -> {
            List<ConnectionConfig> list = connectionService.listConnections();
            return JsonUtil.toCompactJson(ApiResponse.success(list));
        });

        // 添加连接
        post("/api/connections", (req, res) -> {
            ConnectionConfig config = JsonUtil.fromJson(req.body(), ConnectionConfig.class);
            // 自动检测类型
            if (config.getType() == null || config.getType().isEmpty()) {
                config.setType(ConnectionConfig.detectType(config.getJdbcUrl()));
            }
            if ("unknown".equals(config.getType())) {
                return JsonUtil.toCompactJson(ApiResponse.error(400, "无法识别的数据库类型，请指定 type 字段（sqlite/mysql）"));
            }
            ConnectionConfig created = connectionService.addConnection(config);
            logger.info("添加数据库连接: {}", created.getName());
            return JsonUtil.toCompactJson(ApiResponse.success(created, "连接配置已保存"));
        });

        // 测试连接（未保存的配置）——必须在 :id 路由之前注册
        post("/api/connections/test", (req, res) -> {
            ConnectionConfig config = JsonUtil.fromJson(req.body(), ConnectionConfig.class);
            if (config.getType() == null || config.getType().isEmpty()) {
                config.setType(ConnectionConfig.detectType(config.getJdbcUrl()));
            }
            Map<String, Object> result = connectionService.testConnection(config);
            return JsonUtil.toCompactJson(ApiResponse.success(result));
        });

        // ===== 以下为带 :id 参数的路由 =====

        // 获取单个连接详情
        get("/api/connections/:id", (req, res) -> {
            String id = req.params(":id");
            ConnectionConfig config = connectionService.getConnection(id);
            if (config == null) {
                return JsonUtil.toCompactJson(ApiResponse.error(404, "连接不存在"));
            }
            return JsonUtil.toCompactJson(ApiResponse.success(config));
        });

        // 更新连接
        put("/api/connections/:id", (req, res) -> {
            String id = req.params(":id");
            ConnectionConfig config = JsonUtil.fromJson(req.body(), ConnectionConfig.class);
            ConnectionConfig updated = connectionService.updateConnection(id, config);
            return JsonUtil.toCompactJson(ApiResponse.success(updated, "连接配置已更新"));
        });

        // 删除连接
        delete("/api/connections/:id", (req, res) -> {
            String id = req.params(":id");
            boolean ok = connectionService.removeConnection(id);
            if (!ok) {
                return JsonUtil.toCompactJson(ApiResponse.error(404, "连接不存在"));
            }
            return JsonUtil.toCompactJson(ApiResponse.success(null, "连接已删除"));
        });

        // 测试已保存的连接
        post("/api/connections/:id/test", (req, res) -> {
            String id = req.params(":id");
            Map<String, Object> result = connectionService.testSavedConnection(id);
            return JsonUtil.toCompactJson(ApiResponse.success(result));
        });

        // 连接状态
        get("/api/connections/:id/status", (req, res) -> {
            String id = req.params(":id");
            Map<String, Object> status = connectionService.getConnectionStatus(id);
            return JsonUtil.toCompactJson(ApiResponse.success(status));
        });

        // 断开连接
        post("/api/connections/:id/disconnect", (req, res) -> {
            String id = req.params(":id");
            connectionService.disconnect(id);
            return JsonUtil.toCompactJson(ApiResponse.success(null, "连接已断开"));
        });
    }
}

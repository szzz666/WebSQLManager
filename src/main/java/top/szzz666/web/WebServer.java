package top.szzz666.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.szzz666.database.ConnectionManager;
import top.szzz666.model.ApiResponse;
import top.szzz666.service.ConnectionService;
import top.szzz666.service.DataService;
import top.szzz666.service.SqlService;
import top.szzz666.service.TableService;
import top.szzz666.tools.JsonUtil;
import top.szzz666.tools.TaskUtil;
import top.szzz666.web.controller.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static top.szzz666.config.MyConfig.*;
import static spark.Spark.*;

/**
 * SparkJava Web 服务器
 * 负责服务初始化、路由注册、过滤器配置
 */
public class WebServer {
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    private final ConnectionManager connectionManager;
    private final ConnectionService connectionService;
    private final TableService tableService;
    private final DataService dataService;
    private final SqlService sqlService;

    public WebServer(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.connectionService = new ConnectionService(connectionManager);
        this.tableService = new TableService(connectionManager);
        this.dataService = new DataService(connectionManager);
        this.sqlService = new SqlService(connectionManager);
    }

    public void start() {
        // 配置 SparkJava
        port(serverPort);
        if (serverHost != null && !serverHost.isEmpty()) {
            ipAddress(serverHost);
        }
        // 静态资源（CSS/JS等）
        staticFiles.location("/static");
        staticFiles.expireTime(0);

        // 配置 CORS 与 JSON 处理
        configureFilters();

        // 注册路由
        registerAuthRoutes();
        new ConnectionController(connectionService).registerRoutes();
        new TableController(tableService, connectionService).registerRoutes();
        new DataController(dataService, tableService).registerRoutes();
        new SqlController(sqlService).registerRoutes();
        // index.html 通过路由提供，设置 no-cache 头，避免浏览器缓存旧版前端
        get("/", (req, res) -> {
            res.type("text/html;charset=utf-8");
            res.header("Cache-Control", "no-cache, no-store, must-revalidate");
            res.header("Pragma", "no-cache");
            res.header("Expires", "0");
            try (var is = WebServer.class.getResourceAsStream("/static/index.html")) {
                if (is == null) return "<h1>index.html not found</h1>";
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        });

        // 等待初始化完成
        awaitInitialization();
        logger.info("Web 服务已启动: http://{}:{}", serverHost.equals("0.0.0.0") ? "localhost" : serverHost, serverPort);

        // 定期清理过期会话
        TaskUtil.Repeating(SessionManager::cleanup, 5, true, TimeUnit.MINUTES);

        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("正在关闭 Web 服务...");
            stop();
            connectionManager.closeAll();
        }));
    }

    private void configureFilters() {
        // CORS 支持
        before((req, res) -> {
            res.header("Access-Control-Allow-Origin", "*");
            res.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Auth-Token");
            res.header("Access-Control-Max-Age", "3600");
        });
        options("/*", (req, res) -> {
            res.status(200);
            return "";
        });

        // 统一响应类型
        before("/api/*", (req, res) -> res.type("application/json;charset=utf-8"));

        // 认证过滤（排除登录接口）
        before("/api/*", (req, res) -> {
            String path = req.pathInfo();
            // 登录、登出、状态检查接口不需要认证
            if (path.equals("/api/auth/login") || path.equals("/api/auth/status")) {
                return;
            }
            if (!authEnabled) {
                return;
            }
            String token = req.headers("X-Auth-Token");
            if (!SessionManager.validate(token)) {
                halt(401, JsonUtil.toCompactJson(
                        top.szzz666.model.ApiResponse.error(401, "未登录或会话已过期")));
            }
        });

        // 全局异常处理
        exception(Exception.class, (e, req, res) -> {
            logger.error("请求处理异常: {} {}", req.requestMethod(), req.pathInfo(), e);
            res.status(200);
            res.type("application/json;charset=utf-8");
            res.body(JsonUtil.toCompactJson(
                    top.szzz666.model.ApiResponse.error(500, e.getMessage() == null ? "服务器内部错误" : e.getMessage())));
        });

        // 404 处理（API 路径返回 JSON，其他交给静态资源）
        notFound((req, res) -> {
            if (req.pathInfo().startsWith("/api/")) {
                res.type("application/json;charset=utf-8");
                return JsonUtil.toCompactJson(top.szzz666.model.ApiResponse.error(404, "接口不存在: " + req.pathInfo()));
            }
            // 前端 SPA 路由回退到 index.html
            res.redirect("/");
            return "";
        });
    }

    private void registerAuthRoutes() {
        // 登录
        post("/api/auth/login", (req, res) -> {
            var body = JsonUtil.toMap(req.body());
            String username = String.valueOf(body.get("username"));
            String password = String.valueOf(body.get("password"));
            if (!authEnabled) {
                String token = SessionManager.createSession("anonymous");
                return JsonUtil.toCompactJson(top.szzz666.model.ApiResponse.success(
                        java.util.Map.of("token", token, "username", "anonymous"), "认证未启用"));
            }
            if (authUsername.equals(username) && authPassword.equals(password)) {
                String token = SessionManager.createSession(username);
                return JsonUtil.toCompactJson(top.szzz666.model.ApiResponse.success(
                        java.util.Map.of("token", token, "username", username), "登录成功"));
            }
            res.status(401);
            return JsonUtil.toCompactJson(top.szzz666.model.ApiResponse.error(401, "用户名或密码错误"));
        });

        // 登出
        post("/api/auth/logout", (req, res) -> {
            String token = req.headers("X-Auth-Token");
            SessionManager.destroy(token);
            return JsonUtil.toCompactJson(top.szzz666.model.ApiResponse.success(null, "已注销"));
        });

        // 登录状态
        get("/api/auth/status", (req, res) -> {
            String token = req.headers("X-Auth-Token");
            boolean loggedIn = !authEnabled || SessionManager.validate(token);
            Map<String, Object> data = new HashMap<>();
            data.put("loggedIn", loggedIn);
            data.put("authEnabled", authEnabled);
            if (loggedIn && authEnabled) {
                data.put("username", SessionManager.getUsername(token));
            }
            return JsonUtil.toCompactJson(ApiResponse.success(data));
        });
    }
}

package top.szzz666;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.szzz666.config.MyConfig;
import top.szzz666.database.ConnectionManager;
import top.szzz666.web.WebServer;

import java.util.Scanner;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static top.szzz666.config.EasyConfig config;
    @Getter
    private static ConnectionManager connectionManager;

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("  WebSQLManager 数据库管理面板 启动中...");
        logger.info("========================================");

        // 1. 加载配置
        MyConfig.loadConfig();
        logger.info("配置加载完成");

        // 2. 初始化连接管理器
        connectionManager = new ConnectionManager();
        logger.info("连接管理器初始化完成，已加载 {} 个连接配置", connectionManager.listConfigs().size());

        // 3. 启动 Web 服务
        WebServer webServer = new WebServer(connectionManager);
        webServer.start();

        logger.info("========================================");
        logger.info("  WebSQLManager 启动成功！");
        logger.info("  访问地址: http://localhost:{}", top.szzz666.config.MyConfig.serverPort);
        logger.info("  默认账号: admin / admin123");
        logger.info("========================================");

        // 4. 等待关闭命令
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("正在关闭 WebSQLManager...");
            if (connectionManager != null) {
                connectionManager.closeAll();
            }
            logger.info("WebSQLManager 已关闭");
        }));

        // 控制台等待输入（阻塞主线程）
        try (Scanner scanner = new Scanner(System.in)) {
            logger.info("输入 'stop' 或 'exit' 停止服务");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim().toLowerCase();
                if ("stop".equals(line) || "exit".equals(line) || "quit".equals(line)) {
                    logger.info("收到停止命令，正在关闭...");
                    System.exit(0);
                    break;
                } else if ("help".equals(line)) {
                    System.out.println("可用命令: stop/exit/quit - 停止服务 | help - 显示帮助");
                }
            }
        }
    }

}

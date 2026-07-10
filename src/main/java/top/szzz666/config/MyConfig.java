package top.szzz666.config;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

import java.util.ArrayList;

import static top.szzz666.Main.config;

public class MyConfig {

    //日志
    @ConfigItem(key = "log.level", comment = "日志等级: OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, ALL")
    public static String logLevel = "INFO";
    //设置
    @ConfigItem(key = "settings.cordPoolSize", comment = "核心线程数：-1时为自动即 CPU核心数 / 2")
    public static int cordPoolSize = 1;
    @ConfigItem(key = "settings.maxPoolSize", comment = "最大线程数：-1时为自动即 CPU核心数 * 4")
    public static int maxPoolSize = -1;
    @ConfigItem(key = "settings.keepAliveTime", comment = "非核心线程存活时间（秒）")
    public static long keepAliveTime = 60L;
    @ConfigItem(key = "settings.maxQueueSize", comment = "最大队列大小")
    public static int maxQueueSize = 100;


    //Web服务
    @ConfigItem(key = "server.port", comment = "Web服务监听端口")
    public static int serverPort = 8080;
    @ConfigItem(key = "server.host", comment = "Web服务监听地址，0.0.0.0表示所有网卡")
    public static String serverHost = "0.0.0.0";
    @ConfigItem(key = "server.maxQueryRows", comment = "单次查询最大返回行数（防止内存溢出）")
    public static int maxQueryRows = 10000;
    @ConfigItem(key = "server.queryTimeout", comment = "SQL查询超时时间（秒）")
    public static int queryTimeout = 60;

    //安全认证
    @ConfigItem(key = "auth.enabled", comment = "是否启用登录认证")
    public static boolean authEnabled = true;
    @ConfigItem(key = "auth.username", comment = "登录用户名")
    public static String authUsername = "admin";
    @ConfigItem(key = "auth.password", comment = "登录密码")
    public static String authPassword = "admin123";
    @ConfigItem(key = "auth.sessionTimeout", comment = "会话超时时间（分钟）")
    public static int sessionTimeout = 120;




    public static void loadConfig() {
        config = new EasyConfig("config.yml");
        config.loadFromClass(MyConfig.class);
        config.load();
        applyLogLevel();
    }

    /**
     * 根据配置设置日志等级
     */
    public static void applyLogLevel() {
        try {
            Level level = Level.valueOf(logLevel.toUpperCase());
            Configurator.setRootLevel(level);
        } catch (Exception e) {
            Configurator.setRootLevel(Level.INFO);
        }
    }


}

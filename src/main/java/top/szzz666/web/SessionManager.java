package top.szzz666.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static top.szzz666.config.MyConfig.*;

/**
 * 会话管理器（基于内存的 Token 会话）
 */
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private static class Session {
        final String token;
        final String username;
        final long createdAt;
        long lastAccess;
        // 可存储选中数据库连接等上下文
        String activeConnectionId;

        Session(String token, String username) {
            this.token = token;
            this.username = username;
            this.createdAt = System.currentTimeMillis();
            this.lastAccess = this.createdAt;
        }
    }

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    /**
     * 创建会话
     */
    public static String createSession(String username) {
        // 清理旧会话（同一用户只保留一个）
        SESSIONS.entrySet().removeIf(e -> e.getValue().username.equals(username));
        String token = generateToken();
        SESSIONS.put(token, new Session(token, username));
        logger.info("用户 {} 登录成功，创建会话", username);
        return token;
    }

    /**
     * 验证会话
     */
    public static boolean validate(String token) {
        if (token == null || token.isEmpty()) return false;
        Session session = SESSIONS.get(token);
        if (session == null) return false;
        long timeoutMs = sessionTimeout * 60L * 1000L;
        if (System.currentTimeMillis() - session.lastAccess > timeoutMs) {
            SESSIONS.remove(token);
            logger.info("会话超时: {}", session.username);
            return false;
        }
        session.lastAccess = System.currentTimeMillis();
        return true;
    }

    /**
     * 销毁会话
     */
    public static void destroy(String token) {
        Session s = SESSIONS.remove(token);
        if (s != null) logger.info("用户 {} 注销", s.username);
    }

    /**
     * 获取当前用户名
     */
    public static String getUsername(String token) {
        Session s = SESSIONS.get(token);
        return s == null ? null : s.username;
    }

    /**
     * 获取活跃会话数
     */
    public static int activeCount() {
        return SESSIONS.size();
    }

    /**
     * 清理过期会话
     */
    public static void cleanup() {
        long timeoutMs = sessionTimeout * 60L * 1000L;
        long now = System.currentTimeMillis();
        SESSIONS.entrySet().removeIf(e -> now - e.getValue().lastAccess > timeoutMs);
    }

    /**
     * 生成会话 Token
     */
    private static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "") + System.currentTimeMillis();
    }
}

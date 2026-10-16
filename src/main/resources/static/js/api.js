/**
 * API 客户端封装（基于 Axios）
 * 统一处理请求、认证、错误
 */
const Api = (function () {
    const TOKEN_KEY = 'wsm_token';

    function getToken() {
        return localStorage.getItem(TOKEN_KEY) || '';
    }

    function setToken(token) {
        if (token) {
            localStorage.setItem(TOKEN_KEY, token);
        } else {
            localStorage.removeItem(TOKEN_KEY);
        }
    }

    /**
     * 导出接口返回纯文本；后端异常时会返回 JSON 错误体，这里识别后抛出。
     */
    function extractRawText(text) {
        if (typeof text !== 'string') return text;
        const trimmed = text.trim();
        if (!trimmed.startsWith('{')) return text;
        try {
            const parsed = JSON.parse(trimmed);
            // ApiResponse 特征：含数值 code、message 与 timestamp
            if (parsed && typeof parsed.code === 'number' && parsed.code !== 0 && 'timestamp' in parsed) {
                throw new Error(parsed.message || '请求失败');
            }
        } catch (e) {
            if (!(e instanceof SyntaxError)) throw e;
        }
        return text;
    }

    // Axios 实例：所有请求共用基础配置
    const http = axios.create({
        headers: { 'Content-Type': 'application/json' },
    });

    // 请求拦截器：统一注入认证 Token
    http.interceptors.request.use(
        (config) => {
            const token = getToken();
            if (token) {
                config.headers['X-Auth-Token'] = token;
            }
            return config;
        },
        (error) => Promise.reject(error)
    );

    // 响应拦截器：统一解包 { code, message, data }，并把各类失败转换为 Error
    http.interceptors.response.use(
        (response) => {
            const body = response.data;

            // 导出等接口需要原始文本，不做统一解包
            if (response.config && response.config.rawResponse) {
                return extractRawText(body);
            }
            if (!body || typeof body !== 'object') {
                throw new Error('响应解析失败: ' + String(body).substring(0, 200));
            }
            if (body.code !== 0) {
                throw new Error(body.message || '请求失败');
            }
            return body.data;
        },
        (error) => {
            const response = error.response;
            if (!response) {
                throw new Error('网络请求失败: ' + (error.message || '未知错误'));
            }
            const message = (response.data && response.data.message) || '';
            if (response.status === 401) {
                setToken(null);
                // 登录接口的 401 表示账号密码错误，需保留后端提示
                throw new Error(message || '未登录或会话已过期');
            }
            throw new Error(message || ('请求失败 (HTTP ' + response.status + ')'));
        }
    );

    return {
        getToken,
        setToken,
        get: (url) => http.get(url),
        post: (url, data) => http.post(url, data),
        put: (url, data) => http.put(url, data),
        delete: (url, data) => http.delete(url, { data }),

        // 认证
        auth: {
            login: (username, password) =>
                http.post('/api/auth/login', { username, password }),
            logout: () => http.post('/api/auth/logout'),
            status: () => http.get('/api/auth/status'),
        },

        // 连接管理
        connections: {
            list: () => http.get('/api/connections'),
            get: (id) => http.get(`/api/connections/${id}`),
            create: (data) => http.post('/api/connections', data),
            update: (id, data) => http.put(`/api/connections/${id}`, data),
            remove: (id) => http.delete(`/api/connections/${id}`),
            test: (data) => http.post('/api/connections/test', data),
            testSaved: (id) => http.post(`/api/connections/${id}/test`),
            status: (id) => http.get(`/api/connections/${id}/status`),
            disconnect: (id) => http.post(`/api/connections/${id}/disconnect`),
        },

        // 表管理
        tables: {
            list: (connId) => http.get(`/api/connections/${connId}/tables`),
            get: (connId, table) => http.get(`/api/connections/${connId}/tables/${encodeURIComponent(table)}`),
            indexes: (connId, table) => http.get(`/api/connections/${connId}/tables/${encodeURIComponent(table)}/indexes`),
            create: (connId, data) => http.post(`/api/connections/${connId}/tables`, data),
            drop: (connId, table) => http.delete(`/api/connections/${connId}/tables/${encodeURIComponent(table)}`),
            rename: (connId, table, newName) => http.post(`/api/connections/${connId}/tables/${encodeURIComponent(table)}/rename`, { newName }),
            truncate: (connId, table) => http.post(`/api/connections/${connId}/tables/${encodeURIComponent(table)}/truncate`),
            alter: (connId, table, columns) => http.post(`/api/connections/${connId}/tables/${encodeURIComponent(table)}/alter`, { columns }),
            addColumn: (connId, table, col) => http.post(`/api/connections/${connId}/tables/${encodeURIComponent(table)}/columns`, col),
            modifyColumn: (connId, table, col) => http.put(`/api/connections/${connId}/tables/${encodeURIComponent(table)}/columns/${col.name}`, col),
            dropColumn: (connId, table, col) => http.delete(`/api/connections/${connId}/tables/${encodeURIComponent(table)}/columns/${encodeURIComponent(col)}`),
        },

        // 数据 CRUD
        data: {
            query: (connId, table, params) =>
                http.get(`/api/connections/${connId}/data/${encodeURIComponent(table)}`, { params }),
            insert: (connId, table, data) => http.post(`/api/connections/${connId}/data/${encodeURIComponent(table)}`, data),
            batchInsert: (connId, table, rows) => http.post(`/api/connections/${connId}/data/${encodeURIComponent(table)}/batch`, { rows }),
            update: (connId, table, data, where, params) => http.put(`/api/connections/${connId}/data/${encodeURIComponent(table)}`, { data, where, params }),
            delete: (connId, table, where, params) => http.delete(`/api/connections/${connId}/data/${encodeURIComponent(table)}`, { data: { where, params } }),
            updateByPk: (connId, table, data, pk) => http.put(`/api/connections/${connId}/data/${encodeURIComponent(table)}/pk`, { data, primaryKey: pk }),
            deleteByPk: (connId, table, pk) => http.post(`/api/connections/${connId}/data/${encodeURIComponent(table)}/pk/delete`, pk),
            import: (connId, table, format, content, mode) => http.post(`/api/connections/${connId}/data/${encodeURIComponent(table)}/import`, { format, content, mode }),
        },

        // SQL 执行
        sql: {
            execute: (connId, sql) => http.post(`/api/connections/${connId}/sql/execute`, { sql }),
            batch: (connId, sql) => http.post(`/api/connections/${connId}/sql/batch`, { sql }),
            query: (connId, sql, offset, limit) => http.post(`/api/connections/${connId}/sql/query`, { sql, offset, limit }),
            // 导出查询结果（返回原始文本，非 JSON 封装）
            export: (connId, sql, format, tableName, filename) =>
                http.post(`/api/connections/${connId}/sql/export`,
                    { sql, format, tableName, filename },
                    { rawResponse: true, responseType: 'text' }),
        },
    };
})();

/**
 * API 客户端封装
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

    async function request(method, url, data) {
        const options = {
            method: method,
            headers: {
                'Content-Type': 'application/json',
                'X-Auth-Token': getToken(),
            },
        };
        if (data && method !== 'GET') {
            options.body = JSON.stringify(data);
        }
        let resp;
        try {
            resp = await fetch(url, options);
        } catch (e) {
            throw new Error('网络请求失败: ' + e.message);
        }
        // 401 未授权
        if (resp.status === 401) {
            setToken(null);
            throw new Error('未登录或会话已过期');
        }
        let body;
        const text = await resp.text();
        try {
            body = text ? JSON.parse(text) : {};
        } catch (e) {
            body = { code: -1, message: '响应解析失败: ' + text.substring(0, 200) };
        }
        if (body.code !== 0) {
            throw new Error(body.message || '请求失败');
        }
        return body.data;
    }

    return {
        getToken,
        setToken,
        get: (url) => request('GET', url),
        post: (url, data) => request('POST', url, data),
        put: (url, data) => request('PUT', url, data),
        delete: (url, data) => request('DELETE', url, data),

        // 认证
        auth: {
            login: (username, password) =>
                request('POST', '/api/auth/login', { username, password }),
            logout: () => request('POST', '/api/auth/logout'),
            status: () => request('GET', '/api/auth/status'),
        },

        // 连接管理
        connections: {
            list: () => request('GET', '/api/connections'),
            get: (id) => request('GET', `/api/connections/${id}`),
            create: (data) => request('POST', '/api/connections', data),
            update: (id, data) => request('PUT', `/api/connections/${id}`, data),
            remove: (id) => request('DELETE', `/api/connections/${id}`),
            test: (data) => request('POST', '/api/connections/test', data),
            testSaved: (id) => request('POST', `/api/connections/${id}/test`),
            status: (id) => request('GET', `/api/connections/${id}/status`),
            disconnect: (id) => request('POST', `/api/connections/${id}/disconnect`),
        },

        // 表管理
        tables: {
            list: (connId) => request('GET', `/api/connections/${connId}/tables`),
            get: (connId, table) => request('GET', `/api/connections/${connId}/tables/${encodeURIComponent(table)}`),
            indexes: (connId, table) => request('GET', `/api/connections/${connId}/tables/${encodeURIComponent(table)}/indexes`),
            create: (connId, data) => request('POST', `/api/connections/${connId}/tables`, data),
            drop: (connId, table) => request('DELETE', `/api/connections/${connId}/tables/${encodeURIComponent(table)}`),
            rename: (connId, table, newName) => request('POST', `/api/connections/${connId}/tables/${encodeURIComponent(table)}/rename`, { newName }),
            truncate: (connId, table) => request('POST', `/api/connections/${connId}/tables/${encodeURIComponent(table)}/truncate`),
            alter: (connId, table, columns) => request('POST', `/api/connections/${connId}/tables/${encodeURIComponent(table)}/alter`, { columns }),
            addColumn: (connId, table, col) => request('POST', `/api/connections/${connId}/tables/${encodeURIComponent(table)}/columns`, col),
            modifyColumn: (connId, table, col) => request('PUT', `/api/connections/${connId}/tables/${encodeURIComponent(table)}/columns/${col.name}`, col),
            dropColumn: (connId, table, col) => request('DELETE', `/api/connections/${connId}/tables/${encodeURIComponent(table)}/columns/${encodeURIComponent(col)}`),
        },

        // 数据 CRUD
        data: {
            query: (connId, table, params) => {
                const q = new URLSearchParams(params).toString();
                return request('GET', `/api/connections/${connId}/data/${encodeURIComponent(table)}?${q}`);
            },
            insert: (connId, table, data) => request('POST', `/api/connections/${connId}/data/${encodeURIComponent(table)}`, data),
            batchInsert: (connId, table, rows) => request('POST', `/api/connections/${connId}/data/${encodeURIComponent(table)}/batch`, { rows }),
            update: (connId, table, data, where, params) => request('PUT', `/api/connections/${connId}/data/${encodeURIComponent(table)}`, { data, where, params }),
            delete: (connId, table, where, params) => request('DELETE', `/api/connections/${connId}/data/${encodeURIComponent(table)}`, { where, params }),
            updateByPk: (connId, table, data, pk) => request('PUT', `/api/connections/${connId}/data/${encodeURIComponent(table)}/pk`, { data, primaryKey: pk }),
            deleteByPk: (connId, table, pk) => request('POST', `/api/connections/${connId}/data/${encodeURIComponent(table)}/pk/delete`, pk),
            import: (connId, table, format, content, mode) => request('POST', `/api/connections/${connId}/data/${encodeURIComponent(table)}/import`, { format, content, mode }),
        },

        // SQL 执行
        sql: {
            execute: (connId, sql) => request('POST', `/api/connections/${connId}/sql/execute`, { sql }),
            batch: (connId, sql) => request('POST', `/api/connections/${connId}/sql/batch`, { sql }),
            query: (connId, sql, offset, limit) => request('POST', `/api/connections/${connId}/sql/query`, { sql, offset, limit }),
            // 导出查询结果（返回原始文本，非 JSON 封装）
            export: async (connId, sql, format, tableName, filename) => {
                const resp = await fetch(`/api/connections/${connId}/sql/export`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'X-Auth-Token': getToken() },
                    body: JSON.stringify({ sql, format, tableName, filename }),
                });
                if (resp.status === 401) { setToken(null); throw new Error('未登录或会话已过期'); }
                return await resp.text();
            },
        },
    };
})();

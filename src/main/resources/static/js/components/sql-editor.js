/**
 * SQL 查询编辑器组件
 * 使用 CodeMirror 提供语法高亮和自动补全
 */
const SqlEditor = {
    name: 'SqlEditor',
    template: `
    <div class="sql-editor">
        <div class="toolbar">
            <h2 style="margin:0;font-size:20px;color:#303133;">SQL 查询</h2>
            <div class="spacer"></div>
            <el-tooltip content="Ctrl+Enter 执行">
                <el-button type="primary" :icon="VideoPlay" @click="execute" :loading="executing" :disabled="!connId">执行</el-button>
            </el-tooltip>
            <el-tooltip content="批量执行（分号分隔多条）">
                <el-button :icon="Files" @click="executeBatch" :loading="executing" :disabled="!connId">批量执行</el-button>
            </el-tooltip>
            <el-button :icon="Delete" @click="clearEditor" circle/>
        </div>

        <div v-if="!connId" class="empty-state">
            <el-icon class="empty-icon"><EditPen/></el-icon>
            <p>请先选择一个数据库连接</p>
        </div>

        <div v-else class="sql-editor-container" style="height:calc(100vh - 200px);">
            <div ref="editorRef" style="flex:1;overflow:auto;"></div>

            <!-- 执行结果 -->
            <div class="query-result" v-if="results.length > 0">
                <el-tabs v-model="activeResult">
                    <el-tab-pane v-for="(result, idx) in results" :key="idx"
                        :label="resultTab(result, idx)" :name="String(idx)">
                        <!-- 查询结果 -->
                        <template v-if="result.query">
                            <div class="query-result-meta">
                                <span><el-icon><Timer/></el-icon> 耗时 {{ result.elapsed }}ms</span>
                                <span><el-icon><DataLine/></el-icon> {{ result.returnedRows }} 行</span>
                                <el-tag v-if="result.truncated" type="warning" size="small">已截断</el-tag>
                            </div>
                            <el-table :data="result.rows.map(r => arrayToObj(r, result.columns))" border stripe size="small" max-height="400" style="width:100%;">
                                <el-table-column v-for="(col, i) in result.columns" :key="i" :prop="col" :label="col" min-width="130" show-overflow-tooltip>
                                    <template #header>
                                        <div style="line-height:1.3;">
                                            <div style="font-weight:600;">{{ col }}</div>
                                            <div style="font-size:11px;color:#909399;font-weight:400;">{{ result.columnTypes?.[i] }}</div>
                                        </div>
                                    </template>
                                    <template #default="{ row }">{{ formatValue(row[col]) }}</template>
                                </el-table-column>
                            </el-table>
                        </template>
                        <!-- 更新结果 -->
                        <template v-else>
                            <el-result icon="success" :title="'影响行数: ' + result.affectedRows" :sub-title="'耗时 ' + result.elapsed + 'ms'"/>
                        </template>
                    </el-tab-pane>
                </el-tabs>
            </div>

            <!-- 执行历史 -->
            <el-collapse v-if="history.length > 0" style="margin-top:12px;">
                <el-collapse-item title="执行历史" name="history">
                    <div v-for="(h, i) in history" :key="i" style="padding:6px 0;border-bottom:1px solid #f0f0f0;display:flex;align-items:center;gap:8px;">
                        <el-tag :type="h.success ? 'success' : 'danger'" size="small">{{ h.success ? '成功' : '失败' }}</el-tag>
                        <span style="font-size:12px;color:#909399;">{{ h.time }}</span>
                        <code style="flex:1;font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">{{ h.sql }}</code>
                        <el-button size="small" text @click="loadSql(h.sql)">载入</el-button>
                    </div>
                </el-collapse-item>
            </el-collapse>
        </div>
    </div>
    `,
    props: { connId: String },
    setup(props) {
        const { ref, watch, onMounted, nextTick } = Vue;
        const { ElMessage } = ElementPlus;
        const Icons = ElementPlusIconsVue || {};

        const editorRef = ref(null);
        let cmEditor = null;
        const executing = ref(false);
        const results = ref([]);
        const activeResult = ref('0');
        const history = ref([]);

        function arrayToObj(arr, cols) {
            const obj = {};
            if (cols) cols.forEach((c, i) => obj[c] = arr[i]);
            return obj;
        }
        function formatValue(v) {
            if (v == null) return 'NULL';
            if (typeof v === 'object') return JSON.stringify(v);
            return String(v);
        }
        function resultTab(result, idx) {
            if (result.query) return `结果${idx + 1} (${result.returnedRows}行)`;
            return `结果${idx + 1} (${result.affectedRows}行受影响)`;
        }

        function initEditor() {
            if (!editorRef.value || typeof CodeMirror === 'undefined') return;
            cmEditor = CodeMirror(editorRef.value, {
                mode: 'text/x-sql',
                theme: 'dracula',
                lineNumbers: true,
                matchBrackets: true,
                indentUnit: 2,
                tabSize: 2,
                extraKeys: {
                    'Ctrl-Space': 'autocomplete',
                    'Ctrl-Enter': () => execute(),
                    'Cmd-Enter': () => execute(),
                },
            });
            cmEditor.setValue('-- 在此输入 SQL 语句\n-- Ctrl+Enter 执行，Ctrl+Space 自动补全\nSELECT * FROM sqlite_master LIMIT 10;');
        }

        async function execute() {
            if (!props.connId) { ElMessage.warning('请先选择连接'); return; }
            const sql = cmEditor ? cmEditor.getValue().trim() : '';
            if (!sql) { ElMessage.warning('请输入SQL语句'); return; }
            executing.value = true;
            const startTime = Date.now();
            try {
                const result = await Api.sql.execute(props.connId, sql);
                results.value = [result];
                activeResult.value = '0';
                addHistory(sql, true);
                if (result.query) {
                    ElMessage.success(`查询完成，返回 ${result.returnedRows} 行，耗时 ${result.elapsed}ms`);
                } else {
                    ElMessage.success(`执行完成，影响 ${result.affectedRows} 行，耗时 ${result.elapsed}ms`);
                }
            } catch (e) {
                addHistory(sql, false);
                ElMessage.error('执行失败: ' + e.message);
            } finally {
                executing.value = false;
            }
        }

        async function executeBatch() {
            if (!props.connId) { ElMessage.warning('请先选择连接'); return; }
            const sql = cmEditor ? cmEditor.getValue().trim() : '';
            if (!sql) { ElMessage.warning('请输入SQL语句'); return; }
            executing.value = true;
            try {
                const batchResults = await Api.sql.batch(props.connId, sql);
                results.value = batchResults;
                activeResult.value = '0';
                addHistory(sql, true);
                ElMessage.success(`批量执行完成，共 ${batchResults.length} 条语句`);
            } catch (e) {
                addHistory(sql, false);
                ElMessage.error('执行失败: ' + e.message);
            } finally {
                executing.value = false;
            }
        }

        function addHistory(sql, success) {
            history.value.unshift({
                sql: sql.length > 200 ? sql.substring(0, 200) + '...' : sql,
                time: new Date().toLocaleTimeString('zh-CN'),
                success,
            });
            if (history.value.length > 20) history.value.pop();
        }

        function loadSql(sql) {
            if (cmEditor) cmEditor.setValue(sql);
        }

        function clearEditor() {
            if (cmEditor) cmEditor.setValue('');
            results.value = [];
        }

        onMounted(() => {
            nextTick(initEditor);
        });

        watch(() => props.connId, (v) => {
            if (v && !cmEditor) {
                nextTick(initEditor);
            }
        });

        return {
            editorRef, executing, results, activeResult, history,
            arrayToObj, formatValue, resultTab,
            execute, executeBatch, loadSql, clearEditor,
            VideoPlay: Icons.VideoPlay, Files: Icons.Files, Delete: Icons.Delete,
            Timer: Icons.Timer, DataLine: Icons.DataLine, EditPen: Icons.EditPen,
        };
    }
};

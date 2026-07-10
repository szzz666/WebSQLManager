/**
 * 数据浏览器组件
 * 分页查看表数据，支持增删改查与批量操作
 */
const DataBrowser = {
    name: 'DataBrowser',
    template: `
    <div class="data-browser">
        <div class="toolbar">
            <h2 style="margin:0;font-size:20px;color:#303133;">数据浏览</h2>
            <el-select v-model="selectedTable" placeholder="选择数据表" filterable style="width:240px;margin-left:16px;" @change="loadData">
                <el-option v-for="t in tables" :key="t.name" :label="t.name" :value="t.name"/>
            </el-select>
            <div class="spacer"></div>
            <el-button :icon="Refresh" @click="loadData" circle :disabled="!selectedTable"/>
            <el-button type="success" :icon="Plus" @click="openInsert" :disabled="!selectedTable">新增</el-button>
            <el-dropdown @command="fmt => exportTableData(fmt)" :disabled="!selectedTable" style="margin-left:8px;">
                <el-button :icon="Download" :disabled="!selectedTable">导出</el-button>
                <template #dropdown>
                    <el-dropdown-menu>
                        <el-dropdown-item command="csv">导出为 CSV</el-dropdown-item>
                        <el-dropdown-item command="json">导出为 JSON</el-dropdown-item>
                        <el-dropdown-item command="sql">导出为 SQL INSERT</el-dropdown-item>
                    </el-dropdown-menu>
                </template>
            </el-dropdown>
            <el-button :icon="Upload" @click="openImportDialog" :disabled="!selectedTable">导入</el-button>
            <el-button type="danger" :icon="Delete" @click="batchDelete" :disabled="!selectedTable || selectedRows.length === 0">
                批量删除 ({{ selectedRows.length }})
            </el-button>
        </div>

        <div v-if="!selectedTable" class="empty-state">
            <el-icon class="empty-icon"><DataBoard/></el-icon>
            <p>请从上方选择一个数据表开始浏览</p>
        </div>

        <div v-else class="data-table-wrapper">
            <!-- 查询条件 -->
            <div style="padding:10px 12px;background:#fafafa;border-bottom:1px solid #ebeef5;display:flex;gap:8px;align-items:center;flex-wrap:wrap;">
                <el-input v-model="whereClause" placeholder="WHERE 条件（可选），例如: id = 1 AND name LIKE '%test%'" style="flex:1;min-width:300px;" size="small" clearable @keyup.enter="loadData"/>
                <el-button size="small" type="primary" @click="loadData">查询</el-button>
                <el-tooltip content="提示：直接输入WHERE后的条件，不含WHERE关键字">
                    <el-icon><InfoFilled/></el-icon>
                </el-tooltip>
            </div>

            <!-- 数据表格 -->
            <el-table :data="data.rows" v-loading="loading" border stripe @selection-change="onSelectionChange" style="width:100%;" max-height="600">
                <el-table-column type="selection" width="45"/>
                <el-table-column v-for="(col, i) in data.columns" :key="i" :prop="col" :label="col" min-width="140" show-overflow-tooltip>
                    <template #header>
                        <div style="line-height:1.3;">
                            <div style="font-weight:600;">{{ col }}</div>
                            <div style="font-size:11px;color:#909399;font-weight:400;">{{ data.columnTypes?.[i] || '' }}</div>
                        </div>
                    </template>
                    <template #default="{ row }">
                        <span>{{ formatValue(row[col]) }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="140" fixed="right" align="center">
                    <template #default="{ row }">
                        <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
                        <el-button size="small" text type="danger" @click="confirmDeleteRow(row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>

            <!-- 分页 -->
            <div style="padding:12px;display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;">
                <div style="font-size:13px;color:#606266;">
                    共 {{ formatNumber(data.total) }} 条，第 {{ data.page }} / {{ data.totalPages || 1 }} 页
                </div>
                <el-pagination
                    v-model:current-page="page"
                    v-model:page-size="pageSize"
                    :total="data.total"
                    :page-sizes="[10, 20, 50, 100, 200]"
                    layout="sizes, prev, pager, next, jumper"
                    @size-change="loadData"
                    @current-change="loadData"/>
            </div>
        </div>

        <!-- 新增/编辑对话框 -->
        <el-dialog v-model="formVisible" :title="formMode === 'insert' ? '新增记录' : '编辑记录'" width="640px" :close-on-click-modal="false">
            <el-form :model="recordForm" label-width="140px" v-if="tableInfo">
                <el-form-item v-for="col in tableInfo.columns" :key="col.name" :label="col.name">
                    <div style="display:flex;align-items:center;gap:8px;width:100%;">
                        <el-input v-model="recordForm[col.name]" :placeholder="col.type + (col.nullable ? ' (可空)' : ' (必填)')" style="flex:1;"/>
                        <el-tag size="small" :type="col.primaryKey ? 'success' : 'info'">{{ col.fullType || col.type }}</el-tag>
                        <el-tag v-if="col.primaryKey" size="small" type="success">PK</el-tag>
                    </div>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="formVisible = false">取消</el-button>
                <el-button type="primary" @click="saveRecord" :loading="saving">保存</el-button>
            </template>
        </el-dialog>

        <!-- 导入对话框 -->
        <el-dialog v-model="importDialogVisible" :title="'导入数据到 ' + selectedTable" width="640px">
            <el-alert type="warning" :closable="false" style="margin-bottom:16px;">
                CSV 格式：第一行为列名，后续行为数据。JSON 格式：对象数组，key 为列名。
            </el-alert>
            <el-form label-width="80px">
                <el-form-item label="格式">
                    <el-radio-group v-model="importFormat">
                        <el-radio value="csv">CSV</el-radio>
                        <el-radio value="json">JSON</el-radio>
                    </el-radio-group>
                </el-form-item>
                <el-form-item label="冲突处理">
                    <el-radio-group v-model="importMode">
                        <el-radio value="ignore">跳过（遇主键冲突跳过）</el-radio>
                        <el-radio value="replace">覆盖（遇主键冲突替换）</el-radio>
                        <el-radio value="insert">报错（遇主键冲突中止）</el-radio>
                    </el-radio-group>
                </el-form-item>
                <el-form-item label="选择文件">
                    <input type="file" :accept="importFormat === 'csv' ? '.csv' : '.json'" ref="importFileRef" @change="onImportFileChange" style="width:100%;"/>
                </el-form-item>
                <el-form-item label="或粘贴">
                    <el-input v-model="importContent" type="textarea" :rows="6" :placeholder="importFormat === 'csv' ? 'col1,col2,col3\\nval1,val2,val3' : '[{&quot;col1&quot;:&quot;val1&quot;,&quot;col2&quot;:&quot;val2&quot;}]'"/>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="importDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="doImportData" :loading="importingData">导入</el-button>
            </template>
        </el-dialog>
    </div>
    `,
    props: { connId: String, initialTable: String },
    setup(props, { emit }) {
        const { ref, reactive, watch, computed } = Vue;
        const { ElMessage, ElMessageBox } = ElementPlus;
        const Icons = ElementPlusIconsVue || {};

        const tables = ref([]);
        const selectedTable = ref('');
        const loading = ref(false);
        const data = reactive({ rows: [], columns: [], columnTypes: [], total: 0, page: 1, totalPages: 1 });
        const page = ref(1);
        const pageSize = ref(20);
        const whereClause = ref('');
        const selectedRows = ref([]);
        const tableInfo = ref(null);
        const formVisible = ref(false);
        const formMode = ref('insert');
        const recordForm = reactive({});
        const saving = ref(false);
        const originalRow = ref({});

        function formatValue(v) {
            if (v == null) return 'NULL';
            if (typeof v === 'object') return JSON.stringify(v);
            return String(v);
        }
        function formatNumber(n) {
            if (n == null) return 0;
            return Number(n).toLocaleString('zh-CN');
        }

        async function loadTables() {
            if (!props.connId) return;
            try {
                tables.value = await Api.tables.list(props.connId);
                // 自动选中表：优先用 initialTable，否则选第一个表
                if (props.initialTable && tables.value.some(t => t.name === props.initialTable)) {
                    selectedTable.value = props.initialTable;
                } else if (tables.value.length > 0 && !selectedTable.value) {
                    selectedTable.value = tables.value[0].name;
                }
                // 自动加载数据
                if (selectedTable.value) {
                    await loadData();
                }
            } catch (e) { ElMessage.error('加载表列表失败: ' + e.message); }
        }

        async function loadData() {
            if (!selectedTable.value) return;
            loading.value = true;
            selectedRows.value = [];
            try {
                const params = { page: page.value, pageSize: pageSize.value };
                if (whereClause.value.trim()) {
                    params.where = whereClause.value.trim();
                }
                // 后端目前不支持直接传 where，通过 SQL 接口实现条件查询
                if (whereClause.value.trim()) {
                    const escaped = whereClause.value.trim();
                    const sql = 'SELECT * FROM "' + selectedTable.value + '" WHERE ' + escaped;
                    const result = await Api.sql.query(props.connId, sql, (page.value - 1) * pageSize.value, pageSize.value);
                    data.rows = result.rows.map(r => {
                        const obj = {};
                        result.columns.forEach((c, i) => obj[c] = r[i]);
                        return obj;
                    });
                    data.columns = result.columns;
                    data.columnTypes = result.columnTypes;
                    data.total = result.returnedRows < pageSize.value ? (page.value - 1) * pageSize.value + result.returnedRows : -1;
                    // 如果总数未知，单独查询
                    if (data.total < 0) {
                        try {
                            const countResult = await Api.sql.execute(props.connId, 'SELECT COUNT(*) AS cnt FROM "' + selectedTable.value + '" WHERE ' + escaped);
                            data.total = countResult.rows[0][0];
                        } catch { data.total = (page.value - 1) * pageSize.value + result.returnedRows; }
                    }
                    data.page = page.value;
                    data.totalPages = Math.max(1, Math.ceil(data.total / pageSize.value));
                } else {
                    const result = await Api.data.query(props.connId, selectedTable.value, params);
                    Object.assign(data, result);
                }
                // 加载表结构用于编辑
                if (!tableInfo.value || tableInfo.value.name !== selectedTable.value) {
                    tableInfo.value = await Api.tables.get(props.connId, selectedTable.value);
                }
            } catch (e) {
                ElMessage.error('加载数据失败: ' + e.message);
            } finally {
                loading.value = false;
            }
        }

        function onSelectionChange(rows) { selectedRows.value = rows; }

        async function openInsert() {
            formMode.value = 'insert';
            Object.keys(recordForm).forEach(k => delete recordForm[k]);
            // 确保 tableInfo 已加载（数据查询失败时可能未加载）
            if (!tableInfo.value || tableInfo.value.name !== selectedTable.value) {
                try {
                    tableInfo.value = await Api.tables.get(props.connId, selectedTable.value);
                } catch (e) {
                    ElMessage.error('加载表结构失败: ' + e.message);
                    return;
                }
            }
            if (tableInfo.value && tableInfo.value.columns) {
                tableInfo.value.columns.forEach(col => {
                    recordForm[col.name] = col.defaultValue || '';
                });
            }
            formVisible.value = true;
        }

        function openEdit(row) {
            formMode.value = 'update';
            Object.keys(recordForm).forEach(k => delete recordForm[k]);
            Object.assign(recordForm, row);
            originalRow.value = { ...row };
            formVisible.value = true;
        }

        async function saveRecord() {
            saving.value = true;
            try {
                if (formMode.value === 'insert') {
                    // 过滤空值
                    const payload = {};
                    for (const [k, v] of Object.entries(recordForm)) {
                        if (v !== '' && v != null) payload[k] = v;
                    }
                    await Api.data.insert(props.connId, selectedTable.value, payload);
                    ElMessage.success('记录已添加');
                } else {
                    // 构建主键条件
                    const pks = tableInfo.value.primaryKeys.length > 0 ? tableInfo.value.primaryKeys : Object.keys(originalRow.value);
                    const pkValues = {};
                    pks.forEach(k => pkValues[k] = originalRow.value[k]);
                    // 只更新变化的字段
                    const changed = {};
                    for (const [k, v] of Object.entries(recordForm)) {
                        if (String(originalRow.value[k]) !== String(v)) changed[k] = v;
                    }
                    if (Object.keys(changed).length === 0) {
                        ElMessage.info('没有修改');
                        formVisible.value = false;
                        return;
                    }
                    await Api.data.updateByPk(props.connId, selectedTable.value, changed, pkValues);
                    ElMessage.success('记录已更新');
                }
                formVisible.value = false;
                await loadData();
            } catch (e) { ElMessage.error(e.message); }
            finally { saving.value = false; }
        }

        async function confirmDeleteRow(row) {
            try {
                await ElMessageBox.confirm('确定要删除这条记录吗？', '确认', { type: 'warning' });
                const pks = tableInfo.value.primaryKeys.length > 0 ? tableInfo.value.primaryKeys : Object.keys(row);
                const pkValues = {};
                pks.forEach(k => pkValues[k] = row[k]);
                await Api.data.deleteByPk(props.connId, selectedTable.value, pkValues);
                ElMessage.success('记录已删除');
                await loadData();
            } catch (e) { if (e !== 'cancel' && e.message) ElMessage.error(e.message); }
        }

        async function batchDelete() {
            try {
                await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 条记录吗？`, '批量删除', {
                    type: 'warning', confirmButtonText: '确认删除',
                });
                const pks = tableInfo.value.primaryKeys.length > 0 ? tableInfo.value.primaryKeys : (data.columns || []);
                let ok = 0, fail = 0;
                for (const row of selectedRows.value) {
                    try {
                        const pkValues = {};
                        pks.forEach(k => pkValues[k] = row[k]);
                        await Api.data.deleteByPk(props.connId, selectedTable.value, pkValues);
                        ok++;
                    } catch { fail++; }
                }
                ElMessage.success(`删除完成：成功 ${ok} 条${fail > 0 ? '，失败 ' + fail + ' 条' : ''}`);
                await loadData();
            } catch (e) { if (e !== 'cancel' && e.message) ElMessage.error(e.message); }
        }

        // ===== 导出表数据 =====
        const exporting = ref(false);
        async function exportTableData(format) {
            if (!selectedTable.value) return;
            exporting.value = true;
            try {
                const sql = 'SELECT * FROM "' + selectedTable.value + '"';
                const content = await Api.sql.export(
                    props.connId, sql, format, selectedTable.value,
                    `${selectedTable.value}_${new Date().toISOString().slice(0, 10)}.${format}`
                );
                const mime = format === 'json' ? 'application/json' : format === 'sql' ? 'application/sql' : 'text/csv';
                const blob = new Blob([content], { type: mime });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `${selectedTable.value}.${format}`;
                a.click();
                URL.revokeObjectURL(url);
                ElMessage.success('导出完成');
            } catch (e) {
                ElMessage.error('导出失败: ' + e.message);
            } finally {
                exporting.value = false;
            }
        }

        // ===== 导入数据 =====
        const importDialogVisible = ref(false);
        const importFormat = ref('csv');
        const importMode = ref('ignore');
        const importContent = ref('');
        const importingData = ref(false);
        const importFileRef = ref(null);
        const importFileContent = ref('');

        function openImportDialog() {
            importFormat.value = 'csv';
            importMode.value = 'ignore';
            importContent.value = '';
            importFileContent.value = '';
            importDialogVisible.value = true;
        }

        async function onImportFileChange(event) {
            const files = event.target.files;
            if (files && files.length > 0) {
                importFileContent.value = await files[0].text();
            }
        }

        async function doImportData() {
            const content = importFileContent.value || importContent.value;
            if (!content.trim()) {
                ElMessage.warning('请选择文件或粘贴数据');
                return;
            }
            importingData.value = true;
            try {
                const result = await Api.data.import(props.connId, selectedTable.value, importFormat.value, content, importMode.value);
                ElMessage.success(`导入完成: 共 ${result.totalRows} 行，影响 ${result.affectedRows} 行`);
                importDialogVisible.value = false;
                await loadData();
            } catch (e) {
                ElMessage.error('导入失败: ' + e.message);
            } finally {
                importingData.value = false;
            }
        }

        watch(() => props.connId, () => {
            selectedTable.value = '';
            loadTables();
        }, { immediate: true });

        watch(() => props.initialTable, (v) => {
            if (v) { selectedTable.value = v; loadData(); }
        });

        return {
            tables, selectedTable, loading, data, page, pageSize, whereClause,
            selectedRows, tableInfo, formVisible, formMode, recordForm, saving,
            formatValue, formatNumber, loadData, onSelectionChange,
            openInsert, openEdit, saveRecord, confirmDeleteRow, batchDelete,
            exportTableData, exporting,
            importDialogVisible, importFormat, importMode, importContent, importingData,
            importFileRef, openImportDialog, onImportFileChange, doImportData,
            Refresh: Icons.Refresh, Plus: Icons.Plus, Delete: Icons.Delete,
            InfoFilled: Icons.InfoFilled, DataBoard: Icons.DataBoard,
            Download: Icons.Download, Upload: Icons.Upload,
        };
    }
};

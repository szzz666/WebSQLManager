/**
 * 表结构浏览器组件
 * 查看表列表、表结构、索引，支持建表、改表、删表
 */
const TableExplorer = {
    name: 'TableExplorer',
    template: `
    <div class="table-explorer">
        <div class="toolbar">
            <h2 style="margin:0;font-size:20px;color:#303133;">数据表管理</h2>
            <div class="spacer"></div>
            <el-button :icon="Refresh" @click="loadTables" circle/>
            <el-button type="primary" :icon="Plus" @click="openCreateTable">新建表</el-button>
        </div>

        <el-table :data="tables" v-loading="loading" border stripe style="width:100%;" @row-click="openTable">
            <el-table-column prop="name" label="表名" min-width="200">
                <template #default="{ row }">
                    <el-icon><component :is="row.tableType === 'VIEW' ? 'View' : 'Table'"/></el-icon>
                    <span style="margin-left:6px;font-weight:500;color:#409eff;cursor:pointer;">{{ row.name }}</span>
                </template>
            </el-table-column>
            <el-table-column prop="tableType" label="类型" width="120">
                <template #default="{ row }">
                    <el-tag size="small" :type="row.tableType === 'VIEW' ? 'warning' : 'success'">
                        {{ row.tableType === 'VIEW' ? '视图' : '表' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="rowCount" label="行数" width="120" align="right">
                <template #default="{ row }">{{ formatNumber(row.rowCount) }}</template>
            </el-table-column>
            <el-table-column prop="comment" label="备注" min-width="150" show-overflow-tooltip/>
            <el-table-column label="操作" width="260" align="center">
                <template #default="{ row }">
                    <el-button size="small" @click.stop="$emit('browse', row.name)">数据</el-button>
                    <el-button size="small" @click.stop="openTable(row)">结构</el-button>
                    <el-button size="small" type="warning" @click.stop="confirmTruncate(row)">清空</el-button>
                    <el-button size="small" type="danger" @click.stop="confirmDrop(row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>

        <!-- 表结构详情抽屉 -->
        <el-drawer v-model="structureVisible" :title="'表结构 - ' + currentTable" size="70%" destroy-on-close>
            <div v-loading="structureLoading">
                <el-tabs v-model="structureTab">
                    <el-tab-pane label="列信息" name="columns">
                        <div class="toolbar">
                            <el-button type="primary" size="small" :icon="Plus" @click="openAddColumn">添加列</el-button>
                            <div class="spacer"></div>
                        </div>
                        <el-table :data="tableInfo?.columns || []" border stripe size="small">
                            <el-table-column prop="ordinal" label="#" width="50" align="center"/>
                            <el-table-column prop="name" label="列名" min-width="150">
                                <template #default="{ row }">
                                    <span :style="{ fontWeight: row.primaryKey ? 700 : 400, color: row.primaryKey ? '#67c23a' : '#303133' }">{{ row.name }}</span>
                                </template>
                            </el-table-column>
                            <el-table-column label="类型" width="160">
                                <template #default="{ row }">
                                    <span class="type-badge" :class="typeClass(row.type)">{{ row.fullType || row.type }}</span>
                                </template>
                            </el-table-column>
                            <el-table-column label="允许NULL" width="90" align="center">
                                <template #default="{ row }">
                                    <el-tag :type="row.nullable ? 'info' : 'danger'" size="small">{{ row.nullable ? '是' : '否' }}</el-tag>
                                </template>
                            </el-table-column>
                            <el-table-column label="主键" width="70" align="center">
                                <template #default="{ row }">
                                    <el-icon v-if="row.primaryKey" color="#67c23a" :size="16"><Key/></el-icon>
                                </template>
                            </el-table-column>
                            <el-table-column label="自增" width="70" align="center">
                                <template #default="{ row }">
                                    <el-tag v-if="row.autoIncrement" type="warning" size="small">AI</el-tag>
                                </template>
                            </el-table-column>
                            <el-table-column prop="defaultValue" label="默认值" width="120" show-overflow-tooltip/>
                            <el-table-column prop="comment" label="注释" min-width="120" show-overflow-tooltip/>
                            <el-table-column label="操作" width="150" align="center">
                                <template #default="{ row }">
                                    <el-button size="small" text @click="openModifyColumn(row)">修改</el-button>
                                    <el-button size="small" text type="danger" @click="confirmDropColumn(row)">删除</el-button>
                                </template>
                            </el-table-column>
                        </el-table>
                    </el-tab-pane>
                    <el-tab-pane label="索引" name="indexes">
                        <el-table :data="indexes" border stripe size="small">
                            <el-table-column prop="name" label="索引名" min-width="150"/>
                            <el-table-column prop="columnName" label="列名" min-width="150"/>
                            <el-table-column label="类型" width="120">
                                <template #default="{ row }">
                                    <el-tag v-if="row.primaryKey" type="success" size="small">主键</el-tag>
                                    <el-tag v-else-if="row.unique" type="warning" size="small">唯一</el-tag>
                                    <el-tag v-else type="info" size="small">普通</el-tag>
                                </template>
                            </el-table-column>
                            <el-table-column prop="ascOrDesc" label="排序" width="80" align="center"/>
                            <el-table-column prop="ordinal" label="序号" width="80" align="center"/>
                        </el-table>
                    </el-tab-pane>
                    <el-tab-pane label="表信息" name="info">
                        <el-descriptions :column="2" border>
                            <el-descriptions-item label="表名">{{ tableInfo?.name }}</el-descriptions-item>
                            <el-descriptions-item label="类型">{{ tableInfo?.tableType }}</el-descriptions-item>
                            <el-descriptions-item label="行数">{{ formatNumber(tableInfo?.rowCount) }}</el-descriptions-item>
                            <el-descriptions-item label="列数">{{ tableInfo?.columns?.length || 0 }}</el-descriptions-item>
                            <el-descriptions-item label="主键">{{ (tableInfo?.primaryKeys || []).join(', ') || '无' }}</el-descriptions-item>
                            <el-descriptions-item label="备注">{{ tableInfo?.comment || '无' }}</el-descriptions-item>
                        </el-descriptions>
                    </el-tab-pane>
                </el-tabs>
            </div>
        </el-drawer>

        <!-- 新建表对话框 -->
        <el-dialog v-model="createTableVisible" title="新建表" width="720px" :close-on-click-modal="false">
            <el-form :model="newTable" label-width="100px">
                <el-form-item label="表名" required>
                    <el-input v-model="newTable.name" placeholder="例如：users"/>
                </el-form-item>
                <el-form-item label="备注">
                    <el-input v-model="newTable.comment" placeholder="表注释（可选）"/>
                </el-form-item>
                <el-form-item label="列定义" required>
                    <el-table :data="newTable.columns" border size="small" style="width:100%;">
                        <el-table-column label="列名" min-width="120">
                            <template #default="{ row }"><el-input v-model="row.name" size="small" placeholder="column_name"/></template>
                        </el-table-column>
                        <el-table-column label="类型" width="120">
                            <template #default="{ row }">
                                <el-select v-model="row.type" size="small" filterable>
                                    <el-option v-for="t in columnTypes" :key="t" :label="t" :value="t"/>
                                </el-select>
                            </template>
                        </el-table-column>
                        <el-table-column label="长度" width="80">
                            <template #default="{ row }"><el-input-number v-model="row.length" size="small" :min="0" controls-position="right" style="width:100%;"/></template>
                        </el-table-column>
                        <el-table-column label="可空" width="60" align="center">
                            <template #default="{ row }"><el-checkbox v-model="row.nullable"/></template>
                        </el-table-column>
                        <el-table-column label="主键" width="60" align="center">
                            <template #default="{ row }"><el-checkbox v-model="row.primaryKey"/></template>
                        </el-table-column>
                        <el-table-column label="自增" width="60" align="center">
                            <template #default="{ row }"><el-checkbox v-model="row.autoIncrement"/></template>
                        </el-table-column>
                        <el-table-column label="默认值" width="100">
                            <template #default="{ row }"><el-input v-model="row.defaultValue" size="small"/></template>
                        </el-table-column>
                        <el-table-column label="操作" width="70" align="center">
                            <template #default="{ $index }">
                                <el-button size="small" text type="danger" :icon="Delete" @click="newTable.columns.splice($index, 1)" circle/>
                            </template>
                        </el-table-column>
                    </el-table>
                    <el-button size="small" :icon="Plus" @click="addColumnRow" style="margin-top:8px;width:100%;">添加列</el-button>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="createTableVisible = false">取消</el-button>
                <el-button type="primary" @click="createTable" :loading="creating">创建</el-button>
            </template>
        </el-dialog>

        <!-- 添加/修改列对话框 -->
        <el-dialog v-model="columnDialogVisible" :title="columnEditing ? '修改列' : '添加列'" width="500px">
            <el-form :model="columnForm" label-width="100px">
                <el-form-item label="列名" required>
                    <el-input v-model="columnForm.name" :disabled="columnEditing"/>
                </el-form-item>
                <el-form-item label="类型">
                    <el-select v-model="columnForm.type" filterable>
                        <el-option v-for="t in columnTypes" :key="t" :label="t" :value="t"/>
                    </el-select>
                </el-form-item>
                <el-form-item label="长度">
                    <el-input-number v-model="columnForm.length" :min="0"/>
                </el-form-item>
                <el-form-item label="小数位">
                    <el-input-number v-model="columnForm.scale" :min="0"/>
                </el-form-item>
                <el-form-item label="允许NULL">
                    <el-switch v-model="columnForm.nullable"/>
                </el-form-item>
                <el-form-item label="主键">
                    <el-switch v-model="columnForm.primaryKey"/>
                </el-form-item>
                <el-form-item label="自增">
                    <el-switch v-model="columnForm.autoIncrement"/>
                </el-form-item>
                <el-form-item label="默认值">
                    <el-input v-model="columnForm.defaultValue"/>
                </el-form-item>
                <el-form-item label="注释">
                    <el-input v-model="columnForm.comment"/>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="columnDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="saveColumn" :loading="columnSaving">保存</el-button>
            </template>
        </el-dialog>
    </div>
    `,
    props: { connId: String },
    emits: ['browse'],
    setup(props, { emit }) {
        const { ref, reactive, watch } = Vue;
        const { ElMessage, ElMessageBox } = ElementPlus;
        const Icons = ElementPlusIconsVue || {};

        const tables = ref([]);
        const loading = ref(false);
        const structureVisible = ref(false);
        const structureLoading = ref(false);
        const structureTab = ref('columns');
        const currentTable = ref('');
        const tableInfo = ref(null);
        const indexes = ref([]);
        const createTableVisible = ref(false);
        const creating = ref(false);
        const columnDialogVisible = ref(false);
        const columnEditing = ref(false);
        const columnSaving = ref(false);

        const columnTypes = [
            'INT', 'BIGINT', 'SMALLINT', 'TINYINT', 'INTEGER',
            'VARCHAR', 'CHAR', 'TEXT', 'LONGTEXT', 'MEDIUMTEXT', 'CLOB',
            'DECIMAL', 'NUMERIC', 'FLOAT', 'DOUBLE', 'REAL',
            'DATE', 'DATETIME', 'TIMESTAMP', 'TIME',
            'BOOLEAN', 'BLOB', 'BINARY',
        ];

        const newTable = reactive({ name: '', comment: '', columns: [] });
        const columnForm = reactive({ name: '', type: 'VARCHAR', length: 255, scale: 0, nullable: true, primaryKey: false, autoIncrement: false, defaultValue: '', comment: '' });

        function formatNumber(n) {
            if (n == null) return '-';
            return Number(n).toLocaleString('zh-CN');
        }
        function typeClass(type) {
            if (!type) return '';
            const t = type.toUpperCase();
            if (t.includes('INT') || t.includes('INTEGER')) return 'int';
            if (t.includes('CHAR')) return 'varchar';
            if (t.includes('TEXT') || t.includes('CLOB')) return 'text';
            if (t.includes('DATE') || t.includes('TIME')) return 'datetime';
            return '';
        }

        async function loadTables() {
            if (!props.connId) return;
            loading.value = true;
            try {
                tables.value = await Api.tables.list(props.connId);
            } catch (e) {
                ElMessage.error('加载表列表失败: ' + e.message);
            } finally {
                loading.value = false;
            }
        }

        async function openTable(row) {
            currentTable.value = row.name;
            structureVisible.value = true;
            structureTab.value = 'columns';
            structureLoading.value = true;
            try {
                const [info, idx] = await Promise.all([
                    Api.tables.get(props.connId, row.name),
                    Api.tables.indexes(props.connId, row.name).catch(() => []),
                ]);
                tableInfo.value = info;
                indexes.value = idx;
            } catch (e) {
                ElMessage.error('加载表结构失败: ' + e.message);
            } finally {
                structureLoading.value = false;
            }
        }

        function addColumnRow() {
            newTable.columns.push({ name: '', type: 'VARCHAR', length: 255, nullable: true, primaryKey: false, autoIncrement: false, defaultValue: '' });
        }
        function openCreateTable() {
            Object.assign(newTable, { name: '', comment: '', columns: [] });
            addColumnRow();
            createTableVisible.value = true;
        }
        async function createTable() {
            if (!newTable.name) { ElMessage.warning('请输入表名'); return; }
            if (newTable.columns.length === 0) { ElMessage.warning('至少添加一列'); return; }
            for (const c of newTable.columns) {
                if (!c.name) { ElMessage.warning('列名不能为空'); return; }
            }
            creating.value = true;
            try {
                await Api.tables.create(props.connId, { name: newTable.name, comment: newTable.comment, columns: newTable.columns });
                ElMessage.success('表创建成功');
                createTableVisible.value = false;
                await loadTables();
            } catch (e) { ElMessage.error(e.message); }
            finally { creating.value = false; }
        }

        async function confirmDrop(row) {
            try {
                await ElMessageBox.confirm(`确定要删除表「${row.name}」吗？此操作不可恢复！`, '危险操作', {
                    type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消',
                    confirmButtonClass: 'el-button--danger',
                });
                await Api.tables.drop(props.connId, row.name);
                ElMessage.success('表已删除');
                await loadTables();
            } catch (e) { if (e !== 'cancel' && e.message) ElMessage.error(e.message); }
        }

        async function confirmTruncate(row) {
            try {
                await ElMessageBox.confirm(`确定要清空表「${row.name}」的所有数据吗？此操作不可恢复！`, '警告', {
                    type: 'warning', confirmButtonText: '确认清空', cancelButtonText: '取消',
                });
                await Api.tables.truncate(props.connId, row.name);
                ElMessage.success('表已清空');
                await loadTables();
            } catch (e) { if (e !== 'cancel' && e.message) ElMessage.error(e.message); }
        }

        function openAddColumn() {
            columnEditing.value = false;
            Object.assign(columnForm, { name: '', type: 'VARCHAR', length: 255, scale: 0, nullable: true, primaryKey: false, autoIncrement: false, defaultValue: '', comment: '' });
            columnDialogVisible.value = true;
        }
        function openModifyColumn(col) {
            columnEditing.value = true;
            Object.assign(columnForm, col);
            columnDialogVisible.value = true;
        }
        async function saveColumn() {
            if (!columnForm.name) { ElMessage.warning('请输入列名'); return; }
            columnSaving.value = true;
            try {
                if (columnEditing.value) {
                    await Api.tables.modifyColumn(props.connId, currentTable.value, { ...columnForm });
                    ElMessage.success('列已修改');
                } else {
                    await Api.tables.addColumn(props.connId, currentTable.value, { ...columnForm });
                    ElMessage.success('列已添加');
                }
                columnDialogVisible.value = false;
                await openTable({ name: currentTable.value });
            } catch (e) { ElMessage.error(e.message); }
            finally { columnSaving.value = false; }
        }

        async function confirmDropColumn(col) {
            try {
                await ElMessageBox.confirm(`确定要删除列「${col.name}」吗？`, '确认', { type: 'warning' });
                await Api.tables.dropColumn(props.connId, currentTable.value, col.name);
                ElMessage.success('列已删除');
                await openTable({ name: currentTable.value });
            } catch (e) { if (e !== 'cancel' && e.message) ElMessage.error(e.message); }
        }

        watch(() => props.connId, loadTables, { immediate: true });

        return {
            tables, loading, structureVisible, structureLoading, structureTab,
            currentTable, tableInfo, indexes, createTableVisible, creating,
            columnDialogVisible, columnEditing, columnSaving, columnTypes,
            newTable, columnForm,
            formatNumber, typeClass, loadTables, openTable,
            addColumnRow, openCreateTable, createTable, confirmDrop, confirmTruncate,
            openAddColumn, openModifyColumn, saveColumn, confirmDropColumn,
            Refresh: Icons.Refresh, Plus: Icons.Plus, Delete: Icons.Delete, Key: Icons.Key,
        };
    }
};

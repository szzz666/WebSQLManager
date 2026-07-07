/**
 * 连接管理组件
 * 管理数据库连接配置的增删改查、测试
 */
const ConnectionManager = {
    name: 'ConnectionManager',
    template: `
    <div class="connection-manager">
        <div class="toolbar">
            <h2 style="margin:0;font-size:20px;color:#303133;">数据库连接管理</h2>
            <div class="spacer"></div>
            <el-button type="primary" :icon="Plus" @click="openCreate">新建连接</el-button>
        </div>

        <el-empty v-if="connections.length === 0" description="暂无数据库连接，请点击「新建连接」添加">
            <el-button type="primary" @click="openCreate">新建连接</el-button>
        </el-empty>

        <el-row :gutter="16" v-else>
            <el-col v-for="conn in connections" :key="conn.id" :xs="24" :sm="12" :md="8" :lg="6" style="margin-bottom:16px;">
                <el-card class="connection-card" shadow="hover">
                    <template #header>
                        <div style="display:flex;align-items:center;justify-content:space-between;">
                            <div style="display:flex;align-items:center;gap:8px;">
                                <el-icon :size="20" :color="typeColor(conn.type)">
                                    <component :is="typeIcon(conn.type)"/>
                                </el-icon>
                                <span style="font-weight:600;font-size:15px;">{{ conn.name }}</span>
                            </div>
                            <el-dropdown trigger="click" @command="cmd => handleCommand(cmd, conn)">
                                <el-button text :icon="MoreFilled" circle size="small"/>
                                <template #dropdown>
                                    <el-dropdown-menu>
                                        <el-dropdown-item command="test" :icon="Connection">测试连接</el-dropdown-item>
                                        <el-dropdown-item command="edit" :icon="Edit">编辑</el-dropdown-item>
                                        <el-dropdown-item command="disconnect" :icon="SwitchButton" divided>断开</el-dropdown-item>
                                        <el-dropdown-item command="delete" :icon="Delete" style="color:#f56c6c;">删除</el-dropdown-item>
                                    </el-dropdown-menu>
                                </template>
                            </el-dropdown>
                        </div>
                    </template>
                    <div style="font-size:13px;color:#606266;line-height:1.8;">
                        <div><el-icon><Coin/></el-icon> 类型: <el-tag size="small" :type="typeTag(conn.type)">{{ conn.type?.toUpperCase() }}</el-tag></div>
                        <div style="word-break:break-all;"><el-icon><Link/></el-icon> {{ conn.jdbcUrl }}</div>
                        <div v-if="conn.username"><el-icon><User/></el-icon> {{ conn.username }}</div>
                        <div><el-icon><Clock/></el-icon> {{ formatTime(conn.createdAt) }}</div>
                    </div>
                    <div style="margin-top:12px;display:flex;gap:8px;">
                        <el-button type="primary" size="small" @click="$emit('select', conn)" style="flex:1;">
                            <el-icon><Monitor/></el-icon> 进入管理
                        </el-button>
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <!-- 新建/编辑对话框 -->
        <el-dialog v-model="dialogVisible" :title="editing ? '编辑连接' : '新建连接'" width="560px">
            <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
                <el-form-item label="连接名称" prop="name">
                    <el-input v-model="form.name" placeholder="例如：本地MySQL"/>
                </el-form-item>
                <el-form-item label="数据库类型" prop="type">
                    <el-select v-model="form.type" placeholder="选择类型" style="width:100%;">
                        <el-option label="MySQL" value="mysql"/>
                        <el-option label="SQLite" value="sqlite"/>
                    </el-select>
                </el-form-item>
                <el-form-item label="JDBC URL" prop="jdbcUrl">
                    <el-input v-model="form.jdbcUrl" type="textarea" :rows="2"
                        :placeholder="form.type === 'sqlite' ? 'jdbc:sqlite:/path/to/database.db' : 'jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC'"/>
                </el-form-item>
                <el-form-item label="用户名" v-if="form.type !== 'sqlite'">
                    <el-input v-model="form.username" placeholder="数据库用户名"/>
                </el-form-item>
                <el-form-item label="密码" v-if="form.type !== 'sqlite'">
                    <el-input v-model="form.password" type="password" show-password
                        :placeholder="editing ? '留空则不修改密码' : '数据库密码'"/>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="testConnection" :loading="testing">测试连接</el-button>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="save" :loading="saving">保存</el-button>
            </template>
        </el-dialog>
    </div>
    `,
    emits: ['select'],
    setup(props, { emit }) {
        const { ref, reactive, onMounted } = Vue;
        const { ElMessage, ElMessageBox } = ElementPlus;
        const Icons = ElementPlusIconsVue || {};

        const connections = ref([]);
        const dialogVisible = ref(false);
        const editing = ref(false);
        const testing = ref(false);
        const saving = ref(false);
        const formRef = ref(null);

        const form = reactive({
            id: '', name: '', type: 'mysql', jdbcUrl: '', username: '', password: ''
        });

        const rules = {
            name: [{ required: true, message: '请输入连接名称', trigger: 'blur' }],
            type: [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
            jdbcUrl: [{ required: true, message: '请输入JDBC URL', trigger: 'blur' }],
        };

        const typeIcon = (type) => {
            if (type === 'sqlite') return 'Coin';
            if (type === 'mysql') return 'DataLine';
            return 'Files';
        };
        const typeColor = (type) => {
            if (type === 'sqlite') return '#67c23a';
            if (type === 'mysql') return '#409eff';
            return '#909399';
        };
        const typeTag = (type) => {
            if (type === 'sqlite') return 'success';
            if (type === 'mysql') return 'primary';
            return 'info';
        };
        const formatTime = (ts) => {
            if (!ts) return '-';
            return new Date(ts).toLocaleString('zh-CN');
        };

        async function load() {
            try {
                connections.value = await Api.connections.list();
            } catch (e) {
                ElMessage.error('加载连接列表失败: ' + e.message);
            }
        }

        function openCreate() {
            editing.value = false;
            Object.assign(form, { id: '', name: '', type: 'mysql', jdbcUrl: '', username: '', password: '' });
            dialogVisible.value = true;
        }

        function openEdit(conn) {
            editing.value = true;
            Object.assign(form, { ...conn, password: '' });
            dialogVisible.value = true;
        }

        async function save() {
            try {
                await formRef.value.validate();
                saving.value = true;
                if (editing.value) {
                    await Api.connections.update(form.id, { ...form });
                    ElMessage.success('连接已更新');
                } else {
                    await Api.connections.create({ ...form });
                    ElMessage.success('连接已创建');
                }
                dialogVisible.value = false;
                await load();
            } catch (e) {
                if (e.message) ElMessage.error(e.message);
            } finally {
                saving.value = false;
            }
        }

        async function testConnection() {
            try {
                await formRef.value.validate();
                testing.value = true;
                const result = await Api.connections.test({ ...form });
                if (result.success) {
                    ElMessage.success(`连接成功！耗时 ${result.elapsed}ms`);
                } else {
                    ElMessage.error('连接失败: ' + result.message);
                }
            } catch (e) {
                if (e.message) ElMessage.error(e.message);
            } finally {
                testing.value = false;
            }
        }

        async function handleCommand(cmd, conn) {
            if (cmd === 'test') {
                try {
                    const result = await Api.connections.testSaved(conn.id);
                    if (result.success) {
                        ElMessage.success(`连接成功！耗时 ${result.elapsed}ms`);
                    } else {
                        ElMessage.error('连接失败: ' + result.message);
                    }
                } catch (e) { ElMessage.error(e.message); }
            } else if (cmd === 'edit') {
                openEdit(conn);
            } else if (cmd === 'disconnect') {
                try {
                    await Api.connections.disconnect(conn.id);
                    ElMessage.success('连接已断开');
                } catch (e) { ElMessage.error(e.message); }
            } else if (cmd === 'delete') {
                try {
                    await ElMessageBox.confirm(`确定要删除连接「${conn.name}」吗？`, '确认删除', {
                        type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
                    });
                    await Api.connections.remove(conn.id);
                    ElMessage.success('连接已删除');
                    await load();
                } catch (e) {
                    if (e !== 'cancel' && e.message) ElMessage.error(e.message);
                }
            }
        }

        onMounted(load);

        return {
            connections, dialogVisible, editing, testing, saving, formRef, form, rules,
            typeIcon, typeColor, typeTag, formatTime,
            openCreate, save, testConnection, handleCommand,
            Plus: Icons.Plus, MoreFilled: Icons.MoreFilled, Connection: Icons.Connection,
            Edit: Icons.Edit, Delete: Icons.Delete, SwitchButton: Icons.SwitchButton,
            Coin: Icons.Coin, Link: Icons.Link, User: Icons.User, Clock: Icons.Clock,
            Monitor: Icons.Monitor, DataLine: Icons.DataLine, Files: Icons.Files,
        };
    }
};

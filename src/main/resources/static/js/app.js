/**
 * WebSQLManager 主应用
 */
const { createApp, ref, reactive, computed, onMounted, watch } = Vue;

const App = {
    setup() {
        const { ElMessage } = ElementPlus;

        const loggedIn = ref(false);
        const authEnabled = ref(true);
        const username = ref('');
        const loginForm = reactive({ username: '', password: '' });
        const loginLoading = ref(false);

        const activeMenu = ref('connections');
        const sidebarCollapsed = ref(false);
        const mobileSidebarOpen = ref(false);
        const activeConnection = ref(null); // { id, name, type }
        const browseTable = ref(''); // 传递给数据浏览器的初始表名

        // 登录
        async function doLogin() {
            if (!loginForm.username || !loginForm.password) {
                ElMessage.warning('请输入用户名和密码');
                return;
            }
            loginLoading.value = true;
            try {
                const result = await Api.auth.login(loginForm.username, loginForm.password);
                Api.setToken(result.token);
                username.value = result.username;
                loggedIn.value = true;
                ElMessage.success('登录成功');
            } catch (e) {
                ElMessage.error(e.message || '登录失败');
            } finally {
                loginLoading.value = false;
            }
        }

        async function doLogout() {
            try { await Api.auth.logout(); } catch (e) {}
            Api.setToken(null);
            loggedIn.value = false;
            activeConnection.value = null;
            loginForm.password = '';
        }

        async function checkAuth() {
            try {
                const status = await Api.auth.status();
                authEnabled.value = status.authEnabled;
                loggedIn.value = status.loggedIn;
                if (status.loggedIn && status.username) {
                    username.value = status.username;
                }
            } catch (e) {
                loggedIn.value = false;
            }
        }

        function selectConnection(conn) {
            activeConnection.value = { id: conn.id, name: conn.name, type: conn.type };
            activeMenu.value = 'tables';
            mobileSidebarOpen.value = false;
        }

        function selectMenu(menu) {
            activeMenu.value = menu;
            mobileSidebarOpen.value = false;
        }

        function browseData(tableName) {
            browseTable.value = tableName;
            activeMenu.value = 'data';
            mobileSidebarOpen.value = false;
        }

        function toggleSidebar() {
            if (window.innerWidth <= 768) {
                mobileSidebarOpen.value = !mobileSidebarOpen.value;
            } else {
                sidebarCollapsed.value = !sidebarCollapsed.value;
            }
        }

        onMounted(checkAuth);

        // 注册图标
        const icons = ElementPlusIconsVue || {};

        return {
            loggedIn, authEnabled, username, loginForm, loginLoading,
            activeMenu, sidebarCollapsed, mobileSidebarOpen,
            activeConnection, browseTable,
            doLogin, doLogout, selectConnection, selectMenu, browseData, toggleSidebar,
            icons,
        };
    },
    template: `
    <div>
        <!-- 登录页 -->
        <div v-if="!loggedIn" class="login-container">
            <div class="login-card">
                <div class="login-title">🗄️ WebSQLManager</div>
                <div class="login-subtitle">数据库管理面板</div>
                <el-form @submit.prevent="doLogin">
                    <el-form-item>
                        <el-input v-model="loginForm.username" placeholder="用户名" :prefix-icon="icons.User" size="large" @keyup.enter="doLogin"/>
                    </el-form-item>
                    <el-form-item>
                        <el-input v-model="loginForm.password" type="password" placeholder="密码" show-password :prefix-icon="icons.Lock" size="large" @keyup.enter="doLogin"/>
                    </el-form-item>
                    <el-button type="primary" @click="doLogin" :loading="loginLoading" size="large" style="width:100%;">登 录</el-button>
                </el-form>
                <div style="margin-top:16px;font-size:12px;color:#909399;text-align:center;">
                    默认账号: admin / admin123
                </div>
            </div>
        </div>

        <!-- 主界面 -->
        <div v-else class="app-layout">
            <!-- 顶部 -->
            <div class="app-header">
                <div style="display:flex;align-items:center;gap:12px;">
                    <el-button text :icon="icons.Fold" @click="toggleSidebar" v-if="!sidebarCollapsed"/>
                    <el-button text :icon="icons.Expand" @click="toggleSidebar" v-else/>
                    <span class="logo">🗄️ WebSQLManager</span>
                </div>
                <div style="display:flex;align-items:center;gap:12px;">
                    <el-tag v-if="activeConnection" type="primary" effect="dark">
                        <el-icon><Link/></el-icon> {{ activeConnection.name }} ({{ activeConnection.type }})
                    </el-tag>
                    <el-dropdown>
                        <span style="cursor:pointer;display:flex;align-items:center;gap:6px;">
                            <el-avatar :size="28" style="background:#409eff;">{{ username?.charAt(0).toUpperCase() }}</el-avatar>
                            <span style="font-size:14px;">{{ username }}</span>
                        </span>
                        <template #dropdown>
                            <el-dropdown-menu>
                                <el-dropdown-item :icon="icons.SwitchButton" @click="doLogout">退出登录</el-dropdown-item>
                            </el-dropdown-menu>
                        </template>
                    </el-dropdown>
                </div>
            </div>

            <div class="app-body">
                <!-- 侧边栏 -->
                <div class="app-sidebar" :class="{ collapsed: sidebarCollapsed, 'mobile-open': mobileSidebarOpen }">
                    <div class="sidebar-header">
                        <span v-if="!sidebarCollapsed" style="font-weight:600;font-size:14px;">导航菜单</span>
                        <el-icon v-else><Menu/></el-icon>
                    </div>
                    <div class="sidebar-nav">
                        <div class="nav-item" :class="{ active: activeMenu === 'connections' }" @click="selectMenu('connections')">
                            <el-icon><Connection/></el-icon>
                            <span v-if="!sidebarCollapsed">连接管理</span>
                        </div>
                        <template v-if="activeConnection">
                            <div class="nav-item" :class="{ active: activeMenu === 'tables' }" @click="selectMenu('tables')">
                                <el-icon><Grid/></el-icon>
                                <span v-if="!sidebarCollapsed">数据表</span>
                            </div>
                            <div class="nav-item" :class="{ active: activeMenu === 'data' }" @click="selectMenu('data')">
                                <el-icon><DataBoard/></el-icon>
                                <span v-if="!sidebarCollapsed">数据浏览</span>
                            </div>
                            <div class="nav-item" :class="{ active: activeMenu === 'sql' }" @click="selectMenu('sql')">
                                <el-icon><EditPen/></el-icon>
                                <span v-if="!sidebarCollapsed">SQL 查询</span>
                            </div>
                        </template>
                    </div>
                    <div class="sidebar-footer" v-if="!sidebarCollapsed">
                        <div>WebSQLManager v1.0</div>
                        <div>多数据源管理面板</div>
                    </div>
                </div>
                <div class="sidebar-overlay" :class="{ show: mobileSidebarOpen }" @click="mobileSidebarOpen = false"></div>

                <!-- 主内容 -->
                <div class="app-main">
                    <connection-manager v-if="activeMenu === 'connections'" @select="selectConnection"/>
                    <table-explorer v-else-if="activeMenu === 'tables' && activeConnection"
                        :conn-id="activeConnection.id" @browse="browseData"/>
                    <data-browser v-else-if="activeMenu === 'data' && activeConnection"
                        :conn-id="activeConnection.id" :initial-table="browseTable"/>
                    <sql-editor v-else-if="activeMenu === 'sql' && activeConnection"
                        :conn-id="activeConnection.id"/>
                    <div v-else class="empty-state">
                        <el-icon class="empty-icon"><DataBoard/></el-icon>
                        <p>请从左侧选择功能，或先在「连接管理」中选择一个数据库</p>
                    </div>
                </div>
            </div>
        </div>
    </div>
    `,
};

const app = createApp(App);
app.use(ElementPlus, { locale: ElementPlusLocaleZhCn });
// 全局错误处理器，打印到控制台
app.config.errorHandler = (err, instance, info) => {
    console.error('[Vue Error]', err, '\nInfo:', info);
};
// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue || {})) {
    app.component(key, component);
}
// 注册自定义组件
app.component('ConnectionManager', ConnectionManager);
app.component('TableExplorer', TableExplorer);
app.component('DataBrowser', DataBrowser);
app.component('SqlEditor', SqlEditor);
app.mount('#app');

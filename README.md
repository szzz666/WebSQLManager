# WebSQLManager

基于 SparkJava 框架的数据库管理 Web 面板，支持 SQLite 和 MySQL 多数据源管理。

## 功能特性

- **多数据源支持**：SQLite、MySQL（可扩展更多方言）
- **连接管理**：通过 JDBC URL、账号密码建立连接，配置持久化保存
- **表数据导入导出**：针对单个表的数据导入（CSV/JSON）和导出（CSV/JSON/SQL INSERT）
- **表结构管理**：
  - 可视化查看表列表、列结构、索引、主键
  - 创建表、删除表、重命名表、清空表
  - 动态添加、修改、删除列
- **数据 CRUD**：
  - 分页浏览表数据
  - 新增、编辑、删除记录
  - 批量删除
  - 条件查询（WHERE 子句）
- **SQL 查询编辑器**：
  - CodeMirror 语法高亮
  - 自动补全（Ctrl+Space）
  - 单条/批量执行（分号分隔）
  - 结果展示与执行历史
  - 快捷键执行（Ctrl+Enter）
- **安全认证**：基于 Token 的会话认证，可配置用户名密码
- **响应式界面**：Vue 3 + Element Plus，适配桌面与移动端

## 技术栈

### 后端
- **Java 21** + **Maven**
- **SparkJava 2.9.4** — Web 服务与 API 路由
- **SQLite JDBC 3.46** + **MySQL Connector/J 8.4** — 数据库驱动
- **Gson** — JSON 序列化
- **SnakeYAML** — 配置文件
- **Log4j2** — 日志系统
- **Lombok** — 简化代码

### 前端
- **Vue 3** — 响应式框架
- **Element Plus** — UI 组件库
- **CodeMirror 5** — SQL 代码编辑器

## 快速开始

### 编译打包

```bash
mvn clean package
```

### 运行

```bash
java -jar target/WebSQLManager-1.0-SNAPSHOT.jar
```

### 访问

浏览器打开 `http://localhost:8080`

默认账号：`admin` / `admin123`（请在 `config.yml` 中修改）

## 配置说明

首次运行会自动生成 `config.yml` 配置文件：

```yaml
server:
  port: 8080              # Web服务端口
  host: 0.0.0.0           # 监听地址
  maxQueryRows: 10000     # 单次查询最大返回行数
  queryTimeout: 60        # SQL查询超时(秒)
auth:
  enabled: true           # 是否启用登录认证
  username: admin         # 登录用户名
  password: admin123      # 登录密码（请修改）
  sessionTimeout: 120     # 会话超时(分钟)
  secretKey: ...          # 会话签名密钥
```

连接配置保存在 `connections.json` 文件中。

## 架构设计

```
src/main/java/top/szzz666/
├── Main.java                  # 启动入口
├── config/                    # 配置系统
│   ├── ConfigItem.java        # 配置项注解
│   ├── EasyConfig.java        # YAML 配置读写
│   └── MyConfig.java          # 配置定义
├── model/                     # 数据模型
│   ├── ApiResponse.java       # 统一响应封装
│   ├── ConnectionConfig.java  # 连接配置
│   ├── ColumnInfo.java        # 列信息
│   ├── ColumnDefinition.java  # 列定义(建表/改表)
│   ├── TableInfo.java         # 表信息
│   ├── IndexInfo.java         # 索引信息
│   └── QueryResult.java       # 查询结果
├── database/                  # 数据库抽象层
│   ├── DatabaseDialect.java   # 方言接口
│   ├── AbstractDialect.java   # 方言基类(JDBC元数据)
│   ├── SqliteDialect.java     # SQLite 方言
│   ├── MysqlDialect.java      # MySQL 方言
│   ├── DialectFactory.java    # 方言工厂
│   └── ConnectionManager.java # 连接管理器
├── service/                   # 业务服务层
│   ├── ConnectionService.java # 连接管理服务
│   ├── TableService.java      # 表结构服务
│   ├── DataService.java       # 数据CRUD服务
│   └── SqlService.java        # SQL执行服务
├── web/                       # Web层
│   ├── WebServer.java         # SparkJava服务器
│   ├── SessionManager.java    # 会话管理
│   └── controller/            # 控制器
│       ├── ConnectionController.java
│       ├── TableController.java
│       ├── DataController.java
│       └── SqlController.java
└── tools/                     # 工具类
    ├── JsonUtil.java
    ├── FileUtil.java
    ├── TaskUtil.java
    └── ThreadPoolUtil.java
```

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/logout` | 登出 |
| GET | `/api/auth/status` | 登录状态 |
| GET | `/api/connections` | 连接列表 |
| POST | `/api/connections` | 添加连接 |
| PUT | `/api/connections/:id` | 更新连接 |
| DELETE | `/api/connections/:id` | 删除连接 |
| POST | `/api/connections/test` | 测试连接 |
| GET | `/api/connections/:connId/tables` | 表列表 |
| GET | `/api/connections/:connId/tables/:table` | 表结构 |
| POST | `/api/connections/:connId/tables` | 创建表 |
| DELETE | `/api/connections/:connId/tables/:table` | 删除表 |
| POST | `/api/connections/:connId/tables/:table/alter` | 修改表结构 |
| GET | `/api/connections/:connId/data/:table` | 分页查询数据 |
| POST | `/api/connections/:connId/data/:table` | 插入记录 |
| POST | `/api/connections/:connId/data/:table/batch` | 批量插入 |
| PUT | `/api/connections/:connId/data/:table` | 更新记录 |
| DELETE | `/api/connections/:connId/data/:table` | 删除记录 |
| PUT | `/api/connections/:connId/data/:table/pk` | 按主键更新 |
| POST | `/api/connections/:connId/data/:table/pk/delete` | 按主键删除 |
| POST | `/api/connections/:connId/data/:table/import` | 导入表数据(CSV/JSON) |
| POST | `/api/connections/:connId/sql/export` | 导出表数据(CSV/JSON/SQL) |
| POST | `/api/connections/:connId/sql/execute` | 执行SQL |
| POST | `/api/connections/:connId/sql/batch` | 批量执行SQL |
| POST | `/api/connections/:connId/sql/query` | 分页查询 |

## 扩展新数据库类型

1. 实现 `DatabaseDialect` 接口（或继承 `AbstractDialect`）
2. 在 `DialectFactory` 中注册

```java
public class PostgresDialect extends AbstractDialect {
    @Override
    public String name() { return "postgresql"; }
    @Override
    public String driverClassName() { return "org.postgresql.Driver"; }
    @Override
    public String quoteIdentifier(String name) { return "\"" + name + "\""; }
    // ... 其他方法
}
```

## 安全提示

- 生产环境请务必修改默认密码
- `config.yml` 中的 `secretKey` 请改为随机字符串
- SQL 查询编辑器允许执行任意 SQL，请确保仅授权可信用户访问
- 建议部署在内网或增加反向代理与额外认证层

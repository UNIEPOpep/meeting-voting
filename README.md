# Meeting Voting — 会议投票系统

一款基于 Kotlin + Node.js 的安卓会议投票应用，支持发起投票、参会投票、数据汇总，采用双密钥体系实现权限分级管理。

---

## 架构概览

```
┌──────────────────────┐      HTTP/HTTPS       ┌──────────────────────┐
│   Android App (Kotlin) │ ◄──────────────────► │   Backend (Node.js)   │
│   Jetpack Compose      │     JSON + JWT       │   Express + MySQL     │
└──────────────────────┘                        └──────────────────────┘
```

| 层级 | 技术栈 | 版本 |
|------|--------|------|
| 前端 | Kotlin + Jetpack Compose | Compose BOM 2024.02 |
| 后端 | Node.js + Express | 4.21 |
| 数据库 | MySQL | 8.0 |
| 认证 | JWT (HS256) + bcrypt | jsonwebtoken 9.x |
| 安全 | Helmet + Rate Limit + CSP | — |

---

## 权限模型

```
所有用户登录 → 普通用户（只能投票）
       │
       ├── 输入 SA 密钥 () → 超级管理员（创建用户/改密钥/看全部汇总）
       │
       └── 输入 NA 密钥 () → 普通管理员（发起投票/看自己汇总）

退出登录 / 关闭APP → 权限自动失效，恢复为普通用户
```

**核心原则**：数据库中没有任何人是 `super_admin`，权限只能通过密钥临时激活，不在数据库持久化。

---

## 快速开始

### 环境要求

| 工具 | 最低版本 |
|------|----------|
| Node.js | 18+ |
| MySQL | 8.0+ |
| Java JDK | 17+ (Android 编译用) |
| Android SDK | 34+ (编译用) |
| Gradle | 8.4+ (通过 wrapper) |

### 1. 克隆仓库

```bash
git clone https://github.com/UNIEPOpep/meeting-voting.git
cd meeting-voting
```

### 2. 配置后端

```bash
cd server
cp .env.example .env
```

编辑 `.env`，填入你的配置：

```env
PORT=3000
JWT_SECRET=你的随机密钥（运行 openssl rand -base64 32 生成）
JWT_EXPIRES_IN=2h
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=你的数据库密码
DB_NAME=meeting_vote
```

### 3. 初始化数据库

```bash
npm install
npm run db:init
```

首次运行会打印凭据（仅显示一次，请妥善保存）：

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⚠️  以下凭据仅首次初始化时显示，请妥善保存
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   初始用户: 
   登录密码: 
   SA 密钥:   (解锁超管)
   NA 密钥:   (解锁普管)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### 4. 启动后端

```bash
npm start
# 或开发模式（热重载）
npm run dev
```

后端启动在 `http://localhost:3000`，健康检查：`/api/health`

### 5. 编译 Android APK

```bash
cd android
# 创建 local.properties 指定 SDK 路径
echo "sdk.dir=你的Android SDK路径" > local.properties
# 编译调试版
./gradlew assembleDebug
# APK 输出在: app/build/outputs/apk/debug/app-debug.apk
```

---

## API 文档

### 基础信息

- Base URL: `http://<host>:3000/api`
- Content-Type: `application/json`
- 认证: Header `Authorization: Bearer <JWT_TOKEN>`

### 认证接口

#### POST /api/auth/login — 登录

```json
// Request
{ "username": "", "password": "" }

// Response 200
{
  "token": "eyJhbG...",
  "user": { "id": 1, "username": "", "role": "user" }
}

// Response 401
{ "error": "用户名或密码错误" }
```

#### GET /api/auth/me — 获取当前用户

```json
// Response 200
{ "user": { "id": 1, "username": "", "role": "user", "created_at": "..." } }
```

### 管理接口

#### POST /api/admin/unlock — 密钥解锁

```json
// Request — SA 密钥 → 超管
{ "secret_key": "" }

// Request — NA 密钥 → 普管
{ "secret_key": "" }

// Response 200
{
  "message": "超级管理员权限已解锁（本次会话有效）",
  "token": "eyJhbG...",       // 新令牌，替换旧令牌使用
  "unlocked": true,
  "role": "super_admin"
}

// Response 401
{ "error": "密钥错误" }
```

#### PUT /api/admin/secret-key — 修改解锁密钥

```json
// Request
{ "key_type": "sa", "old_key": "旧密钥", "new_key": "新密钥至少8位" }

// key_type: "sa" 修改超管密钥, "na" 修改普管密钥
// 仅超管可修改 SA 密钥，普管可修改 NA 密钥

// Response 200
{ "message": "超管密钥修改成功" }
```

### 用户管理（超管）

#### POST /api/users — 创建用户

```json
// Request
{ "username": "zhangsan", "password": "12345678", "role": "user" }
// role: "user" 或 "admin"

// Response 201
{ "message": "用户创建成功" }
```

#### GET /api/users — 用户列表

```json
// Response 200
{ "users": [{ "id": 1, "username": "", "role": "user", "created_at": "..." }] }
```

### 投票接口

#### POST /api/voting-sessions — 发起投票（管理员）

```json
// Request
{
  "topic": "2026年度预算审批",
  "file_number": "ABC001",
  "vote_password": "123456",
  "deadline": "2026-12-31T18:00:00",
  "allow_abstain": true,
  "allow_change_vote": false
}

// Response 201
{ "id": 1, "message": "投票创建成功" }
```

#### POST /api/voting-sessions/join — 加入投票

```json
// Request
{ "file_number": "ABC001", "vote_password": "123456" }

// Response 200
{
  "session_id": 1,
  "topic": "2026年度预算审批",
  "file_number": "ABC001",
  "deadline": "2026-12-31T18:00:00.000Z",
  "allow_abstain": true,
  "allow_change_vote": false,
  "my_vote": null              // null = 未投, {choice, voted_at} = 已投
}

// Response 404/401
{ "error": "文件编号或投票密码错误" }
```

#### POST /api/voting-sessions/:id/vote — 提交投票

```json
// Request
{ "choice": "agree" }     // "agree" | "oppose" | "abstain"

// Response 201
{ "message": "投票成功" }

// Response 400 — 已投过
{ "error": "您已投过票了" }

// Response 400 — 已截止
{ "error": "投票已截止" }
```

#### PUT /api/voting-sessions/:id/vote — 修改投票

```json
// Request（同上）
{ "choice": "oppose" }

// Response 200
{ "message": "投票修改成功" }

// Response 400 — 不允许修改
{ "error": "该投票不允许修改" }
```

#### GET /api/voting-sessions/:id — 投票汇总

```json
// Response 200
{
  "id": 1,
  "topic": "2026年度预算审批",
  "file_number": "ABC001",
  "deadline": "2026-12-31T18:00:00.000Z",
  "allow_abstain": true,
  "allow_change_vote": false,
  "created_by": "",
  "created_at": "2026-07-24T...",
  "summary": {
    "total": 20,
    "agree": { "count": 15, "percent": "75.0%" },
    "oppose": { "count": 3, "percent": "15.0%" },
    "abstain": { "count": 2, "percent": "10.0%" }
  },
  "details": [
    { "username": "zhangsan", "choice": "agree", "voted_at": "...", "updated_at": null }
  ]
}
```

#### GET /api/voting-sessions — 投票列表

权限过滤：
- **超管**：查看全部投票
- **普管**：仅查看自己发起的投票
- **普通用户**：仅查看自己参与过的投票

```json
// Response 200
{
  "sessions": [
    {
      "id": 1, "topic": "...", "file_number": "ABC001",
      "deadline": "...", "vote_count": 5, "created_by_name": "TFS",
      "allow_abstain": true, "allow_change_vote": false
    }
  ]
}
```

---

## 数据库设计

### ER 图

```
┌──────────┐       ┌──────────────────┐       ┌──────────┐
│  users   │       │  voting_sessions  │       │  votes   │
├──────────┤       ├──────────────────┤       ├──────────┤
│ id (PK)  │──┐    │ id (PK)          │    ┌──│ id (PK)  │
│ username │  │    │ topic            │    │  │ session_id(FK)
│ password │  │    │ file_number      │◄───┘  │ user_id (FK)
│ role     │  ├───►│ vote_password_hash│      │ choice   │
│ created_at│ │    │ deadline         │      │ voted_at │
└──────────┘  │    │ allow_abstain    │      │ updated_at│
              │    │ allow_change_vote│      └──────────┘
              │    │ created_by (FK)  │
              │    │ created_at       │      ┌────────────────┐
              │    └──────────────────┘      │ system_config  │
              │                              ├────────────────┤
              └──────────────────────────────│ config_key (PK)│
                                             │ config_value   │
                                             └────────────────┘
```

### 表详情

**users** — 用户表

| 列 | 类型 | 说明 |
|------|------|------|
| id | INT AUTO_INCREMENT | 主键 |
| username | VARCHAR(50) UNIQUE | 登录账号 |
| password_hash | VARCHAR(255) | bcrypt 哈希 |
| role | ENUM('super_admin','admin','user') | 角色（数据库中仅 admin 和 user） |
| created_at | DATETIME | 创建时间 |
| created_by | INT FK→users.id | 创建者 |

**voting_sessions** — 投票会话

| 列 | 类型 | 说明 |
|------|------|------|
| id | INT AUTO_INCREMENT | 主键 |
| topic | VARCHAR(200) | 投票主题 |
| file_number | VARCHAR(50) | 文件编号（唯一） |
| vote_password_hash | VARCHAR(255) | 投票密码 bcrypt |
| deadline | DATETIME | 截止时间 |
| allow_abstain | TINYINT(1) | 是否允许弃权 |
| allow_change_vote | TINYINT(1) | 是否允许改票 |
| created_by | INT FK→users.id | 发起人 |

**votes** — 投票记录

| 列 | 类型 | 说明 |
|------|------|------|
| id | INT AUTO_INCREMENT | 主键 |
| session_id | INT FK→voting_sessions.id | 投票ID |
| user_id | INT FK→users.id | 用户ID |
| choice | ENUM('agree','oppose','abstain') | 选择 |
| voted_at | DATETIME | 投票时间 |
| updated_at | DATETIME | 改票时间 |
| UNIQUE(session_id, user_id) | — | 每人每票一条记录 |

**system_config** — 系统配置

| 列 | 类型 | 说明 |
|------|------|------|
| config_key | VARCHAR(50) PK | 配置名 |
| config_value | VARCHAR(255) | 配置值 |
| sa_secret_key | — | SA 解锁密钥（bcrypt） |
| na_secret_key | — | NA 解锁密钥（bcrypt） |

---

## 项目结构

```
meeting-voting/
├── README.md                    # 项目文档（本文件）
├── CLAUDE.md                    # 产品设计文档（中文）
├── .gitignore
│
├── server/                      # 后端 (Node.js)
│   ├── package.json
│   ├── .env.example             # 环境变量模板
│   ├── .env                     # 环境变量（不提交）
│   └── src/
│       ├── index.js             # 入口 + 中间件
│       ├── config/
│       │   └── database.js     # MySQL 连接池
│       ├── middleware/
│       │   └── auth.js         # JWT 认证中间件
│       ├── db/
│       │   └── init.js         # 数据库初始化脚本
│       └── routes/
│           ├── auth.js         # 登录 + 用户信息
│           ├── admin.js        # 密钥解锁 + 改密钥
│           ├── users.js        # 用户管理（超管）
│           └── voting.js       # 投票核心（6个接口）
│
└── android/                     # 前端 (Kotlin)
    ├── build.gradle.kts         # 项目级 Gradle
    ├── settings.gradle.kts
    ├── gradle.properties
    └── app/
        ├── build.gradle.kts     # 应用级 Gradle
        └── src/main/
            ├── AndroidManifest.xml
            ├── res/
            │   ├── values/strings.xml
            │   ├── values/themes.xml
            │   └── xml/
            │       ├── network_security_config.xml
            │       └── data_extraction_rules.xml
            └── java/com/heima/vote/
                ├── MainActivity.kt
                ├── data/
                │   ├── api/
                │   │   ├── ApiService.kt       # Retrofit 接口
                │   │   ├── RetrofitClient.kt   # 网络客户端
                │   │   └── TokenManager.kt     # JWT 存储
                │   └── model/
                │       ├── AuthModels.kt
                │       ├── VoteModels.kt
                │       └── AdminModels.kt
                ├── viewmodel/
                │   ├── AuthViewModel.kt
                │   ├── VoteViewModel.kt
                │   └── AdminViewModel.kt
                └── ui/
                    ├── theme/
                    │   ├── Color.kt
                    │   └── Theme.kt
                    └── screens/
                        ├── LoginScreen.kt
                        ├── HomeScreen.kt
                        └── AdminScreen.kt
```

---

## 安全特性

- ✅ JWT 随机密钥，无硬编码默认值
- ✅ bcrypt 哈希存储所有密码和密钥
- ✅ Helmet 安全 HTTP 头
- ✅ 登录 5次/分钟 + 解锁 5次/分钟 严格限流
- ✅ CORS 来源限制
- ✅ 参数化 SQL 查询防注入
- ✅ 登录统一错误提示防用户枚举
- ✅ INSERT IGNORE 防并发竞态
- ✅ Android 关闭 allowBackup + cleartext
- ✅ 生产版关闭 HTTP 日志
- ✅ Authorization 头从日志过滤
- ✅ 数据库密码启动时强制校验

---

## 许可

内部项目，自用为主。

---

## 贡献

欢迎提交 Issue 和 Pull Request。

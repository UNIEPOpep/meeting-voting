// 数据库初始化脚本：创建表 + 预置初始用户
require('dotenv').config();
const mysql = require('mysql2/promise');
const bcrypt = require('bcryptjs');

const dbPassword = process.env.DB_PASSWORD;
const DB_CONFIG = {
  host: process.env.DB_HOST || 'localhost',
  port: process.env.DB_PORT || 3306,
  user: process.env.DB_USER || 'root',
  password: dbPassword || '',
};

const DB_NAME = process.env.DB_NAME || 'meeting_vote';

// TFS 也是普通用户，登录后通过SA/NA密钥切身份
const DEFAULT_SUPER_ADMIN = {
  username: 'TFS',
  password: 'TFS20241114',
};

const DEFAULT_SA_KEY = 'TFSSA20241114';   // 超级管理员解锁密钥
const DEFAULT_NA_KEY = 'TFSNA20241114';   // 普通管理员解锁密钥

async function init() {
  // 1. 连接 MySQL（不指定数据库，先建库）
  const conn = await mysql.createConnection(DB_CONFIG);
  console.log('已连接 MySQL');

  // 2. 创建数据库
  await conn.execute(
    `CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`
  );
  console.log(`数据库 "${DB_NAME}" 已就绪`);

  // 3. 切换到目标数据库
  await conn.query(`USE \`${DB_NAME}\``);

  // 4. 建表
  await conn.execute(`
    CREATE TABLE IF NOT EXISTS users (
      id INT AUTO_INCREMENT PRIMARY KEY,
      username VARCHAR(50) NOT NULL UNIQUE,
      password_hash VARCHAR(255) NOT NULL,
      role ENUM('super_admin', 'admin', 'user') NOT NULL DEFAULT 'user',
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      created_by INT NULL,
      FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);
  console.log('表 "users" 已就绪');

  await conn.execute(`
    CREATE TABLE IF NOT EXISTS voting_sessions (
      id INT AUTO_INCREMENT PRIMARY KEY,
      topic VARCHAR(200) NOT NULL,
      file_number VARCHAR(50) NOT NULL,
      vote_password_hash VARCHAR(255) NOT NULL,
      deadline DATETIME NOT NULL,
      allow_abstain TINYINT(1) NOT NULL DEFAULT 1,
      allow_change_vote TINYINT(1) NOT NULL DEFAULT 0,
      created_by INT NOT NULL,
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);
  console.log('表 "voting_sessions" 已就绪');

  await conn.execute(`
    CREATE TABLE IF NOT EXISTS votes (
      id INT AUTO_INCREMENT PRIMARY KEY,
      session_id INT NOT NULL,
      user_id INT NOT NULL,
      choice ENUM('agree', 'oppose', 'abstain') NOT NULL,
      voted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME NULL,
      UNIQUE KEY unique_vote (session_id, user_id),
      FOREIGN KEY (session_id) REFERENCES voting_sessions(id) ON DELETE CASCADE,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);
  console.log('表 "votes" 已就绪');

  await conn.execute(`
    CREATE TABLE IF NOT EXISTS system_config (
      config_key VARCHAR(50) PRIMARY KEY,
      config_value VARCHAR(255) NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);
  console.log('表 "system_config" 已就绪');

  // 5. 初始化默认数据

  // 检查 TFS 是否已存在
  const [rows] = await conn.execute(
    'SELECT id, role FROM users WHERE username = ?',
    [DEFAULT_SUPER_ADMIN.username]
  );

  if (rows.length === 0) {
    const hash = await bcrypt.hash(DEFAULT_SUPER_ADMIN.password, 10);
    await conn.execute(
      'INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)',
      [DEFAULT_SUPER_ADMIN.username, hash, 'user']
    );
    console.log(`已创建初始用户: ${DEFAULT_SUPER_ADMIN.username} (角色: user)`);
  } else {
    // 确保已存在的 TFS 角色为 user（防止旧版本遗留的 super_admin）
    if (rows[0].role !== 'user') {
      await conn.execute('UPDATE users SET role = ? WHERE username = ?', ['user', DEFAULT_SUPER_ADMIN.username]);
      console.log(`已将 ${DEFAULT_SUPER_ADMIN.username} 角色从 ${rows[0].role} 重置为 user`);
    } else {
      console.log(`初始用户 "${DEFAULT_SUPER_ADMIN.username}" 已存在，跳过`);
    }
  }

  // 初始化 SA 解锁密钥（→ super_admin）
  const [saRows] = await conn.execute(
    'SELECT config_value FROM system_config WHERE config_key = ?',
    ['sa_secret_key']
  );
  if (saRows.length === 0) {
    const keyHash = await bcrypt.hash(DEFAULT_SA_KEY, 10);
    await conn.execute(
      'INSERT INTO system_config (config_key, config_value) VALUES (?, ?)',
      ['sa_secret_key', keyHash]
    );
  }

  // 初始化 NA 解锁密钥（→ admin）
  const [naRows] = await conn.execute(
    'SELECT config_value FROM system_config WHERE config_key = ?',
    ['na_secret_key']
  );
  if (naRows.length === 0) {
    const keyHash = await bcrypt.hash(DEFAULT_NA_KEY, 10);
    await conn.execute(
      'INSERT INTO system_config (config_key, config_value) VALUES (?, ?)',
      ['na_secret_key', keyHash]
    );
  }

  await conn.end();
  console.log('\n✅ 数据库初始化完成！');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('⚠️  以下凭据仅首次初始化时显示，请妥善保存');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log(`   初始用户: ${DEFAULT_SUPER_ADMIN.username}`);
  console.log(`   登录密码: ${DEFAULT_SUPER_ADMIN.password}`);
  console.log(`   SA 密钥: ${DEFAULT_SA_KEY}  (解锁超管)`);
  console.log(`   NA 密钥: ${DEFAULT_NA_KEY}  (解锁普管)`);
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('⚠️  生产环境请立即修改以上所有凭据！');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
}

init().catch((err) => {
  console.error('❌ 初始化失败:', err.message);
  process.exit(1);
});

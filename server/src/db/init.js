// 数据库初始化脚本：创建表 + 预置超级管理员
require('dotenv').config();
const mysql = require('mysql2/promise');
const bcrypt = require('bcryptjs');

const DB_CONFIG = {
  host: process.env.DB_HOST || 'localhost',
  port: process.env.DB_PORT || 3306,
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '',
};

const DB_NAME = process.env.DB_NAME || 'meeting_vote';

// 随机生成强密码（首次运行）
const crypto = require('crypto');
const DEFAULT_SUPER_ADMIN = {
  username: 'superadmin',
  password: crypto.randomBytes(8).toString('hex'),   // 随机生成16位密码
};

const DEFAULT_SECRET_KEY = crypto.randomBytes(6).toString('hex');  // 随机生成12位密钥

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

  // 检查超管是否已存在
  const [rows] = await conn.execute(
    'SELECT id FROM users WHERE username = ?',
    [DEFAULT_SUPER_ADMIN.username]
  );

  if (rows.length === 0) {
    const hash = await bcrypt.hash(DEFAULT_SUPER_ADMIN.password, 10);
    await conn.execute(
      'INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)',
      [DEFAULT_SUPER_ADMIN.username, hash, 'super_admin']
    );
    console.log(`已创建超级管理员: ${DEFAULT_SUPER_ADMIN.username}`);
    console.log(`  密码: ${DEFAULT_SUPER_ADMIN.password}`);
  } else {
    console.log(`超级管理员 "${DEFAULT_SUPER_ADMIN.username}" 已存在，跳过`);
  }

  // 初始化解锁密钥
  const [configRows] = await conn.execute(
    'SELECT config_value FROM system_config WHERE config_key = ?',
    ['admin_secret_key']
  );

  if (configRows.length === 0) {
    const keyHash = await bcrypt.hash(DEFAULT_SECRET_KEY, 10);
    await conn.execute(
      'INSERT INTO system_config (config_key, config_value) VALUES (?, ?)',
      ['admin_secret_key', keyHash]
    );
    console.log(`已设置默认解锁密钥: ${DEFAULT_SECRET_KEY}`);
  } else {
    console.log('解锁密钥已存在，跳过');
  }

  await conn.end();
  console.log('\n✅ 数据库初始化完成！');
}

init().catch((err) => {
  console.error('❌ 初始化失败:', err.message);
  process.exit(1);
});

// 数据库连接配置
const mysql = require('mysql2/promise');
require('dotenv').config();

// 启动时校验数据库密码已设置
const dbPassword = process.env.DB_PASSWORD;
if (!dbPassword) {
  console.error('❌ 错误: 环境变量 DB_PASSWORD 未设置！');
  console.error('   请编辑 server/.env 文件，设置数据库密码');
  console.error('   示例: DB_PASSWORD=你的密码');
  process.exit(1);
}

const pool = mysql.createPool({
  host: process.env.DB_HOST || 'localhost',
  port: process.env.DB_PORT || 3306,
  user: process.env.DB_USER || 'root',
  password: dbPassword,
  database: process.env.DB_NAME || 'meeting_vote',
  waitForConnections: true,
  connectionLimit: 10,
});

module.exports = pool;

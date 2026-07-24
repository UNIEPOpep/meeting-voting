// Meeting Voting - 后端服务入口
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
require('dotenv').config();

const authRoutes = require('./routes/auth');
const usersRoutes = require('./routes/users');
const adminRoutes = require('./routes/admin');
const votingRoutes = require('./routes/voting');

const app = express();
const PORT = process.env.PORT || 3000;

// === 安全中间件 ===

// 安全 HTTP 头
app.use(helmet());

// CORS — 限制来源（生产环境改为实际域名）
app.use(cors({
  origin: process.env.CORS_ORIGIN || 'http://localhost:3000',
  methods: ['GET', 'POST', 'PUT', 'DELETE'],
  allowedHeaders: ['Content-Type', 'Authorization'],
}));

app.use(express.json({ limit: '1mb' }));

// 全局速率限制（100次/分钟）
const globalLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 100,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: '请求过于频繁，请稍后再试' },
});
app.use('/api', globalLimiter);

// 登录接口严格限制（5次/分钟）
const loginLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 5,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: '登录尝试过于频繁，请1分钟后再试' },
});

// 密钥解锁严格限制（5次/分钟）
const unlockLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 5,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: '密钥尝试过于频繁，请1分钟后再试' },
});

// 路由
app.use('/api/auth/login', loginLimiter);
app.post('/api/admin/unlock', unlockLimiter);  // 仅 POST 受限，防止 GET 耗尽配额
app.use('/api/auth', authRoutes);
app.use('/api/users', usersRoutes);
app.use('/api/admin', adminRoutes);
app.use('/api/voting-sessions', votingRoutes);

// 健康检查
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', time: new Date().toISOString() });
});

// 启动服务器
app.listen(PORT, '0.0.0.0', () => {
  console.log(`✅ Meeting Voting 后端已启动: http://0.0.0.0:${PORT}`);
  console.log(`   健康检查: http://localhost:${PORT}/api/health`);
});

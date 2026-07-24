// JWT 认证中间件
const jwt = require('jsonwebtoken');
require('dotenv').config();

const JWT_SECRET = process.env.JWT_SECRET || 'heima_vote_jwt_secret_2026';

// 验证 JWT 令牌（必须登录）
function authRequired(req, res, next) {
  const header = req.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    return res.status(401).json({ error: '请先登录' });
  }

  const token = header.split(' ')[1];
  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    req.user = decoded; // { id, username, role }
    next();
  } catch (err) {
    return res.status(401).json({ error: '登录已过期，请重新登录' });
  }
}

// 验证管理员权限（普通管理员或超管）
function adminRequired(req, res, next) {
  if (req.user.role !== 'admin' && req.user.role !== 'super_admin') {
    return res.status(403).json({ error: '权限不足，需要管理员权限' });
  }
  next();
}

// 验证超级管理员权限
function superAdminRequired(req, res, next) {
  if (req.user.role !== 'super_admin') {
    return res.status(403).json({ error: '权限不足，仅超级管理员可操作' });
  }
  next();
}

// 生成 JWT 令牌
function generateToken(user) {
  return jwt.sign(
    { id: user.id, username: user.username, role: user.role },
    JWT_SECRET,
    { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
  );
}

module.exports = { authRequired, adminRequired, superAdminRequired, generateToken };

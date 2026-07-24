// 认证路由：登录、获取当前用户
const express = require('express');
const bcrypt = require('bcryptjs');
const pool = require('../config/database');
const { authRequired, generateToken } = require('../middleware/auth');

const router = express.Router();

// POST /api/auth/login — 用户登录
router.post('/login', async (req, res) => {
  try {
    const { username, password } = req.body;

    if (!username || !password) {
      return res.status(400).json({ error: '请输入账号和密码' });
    }

    const [rows] = await pool.execute(
      'SELECT id, username, password_hash, role FROM users WHERE username = ?',
      [username]
    );

    if (rows.length === 0) {
      return res.status(401).json({ error: '用户名或密码错误' });
    }

    const user = rows[0];
    const match = await bcrypt.compare(password, user.password_hash);
    if (!match) {
      return res.status(401).json({ error: '用户名或密码错误' });
    }

    const token = generateToken(user);
    res.json({
      token,
      user: { id: user.id, username: user.username, role: user.role },
    });
  } catch (err) {
    console.error('登录失败:', err);
    res.status(500).json({ error: '服务器内部错误' });
  }
});

// GET /api/auth/me — 获取当前用户信息
router.get('/me', authRequired, async (req, res) => {
  try {
    const [rows] = await pool.execute(
      'SELECT id, username, role, created_at FROM users WHERE id = ?',
      [req.user.id]
    );
    if (rows.length === 0) {
      return res.status(404).json({ error: '用户不存在' });
    }
    res.json({ user: rows[0] });
  } catch (err) {
    console.error('获取用户信息失败:', err);
    res.status(500).json({ error: '服务器内部错误' });
  }
});

module.exports = router;

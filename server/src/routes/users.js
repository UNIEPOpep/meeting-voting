// 用户管理路由（仅超级管理员）
const express = require('express');
const bcrypt = require('bcryptjs');
const pool = require('../config/database');
const { authRequired, superAdminRequired } = require('../middleware/auth');

const router = express.Router();

// POST /api/users — 创建新用户
router.post('/', authRequired, superAdminRequired, async (req, res) => {
  try {
    const { username, password, role } = req.body;

    if (!username || !password) {
      return res.status(400).json({ error: '请输入账号和密码' });
    }

    const validRoles = ['admin', 'user'];
    const targetRole = role || 'user';
    if (!validRoles.includes(targetRole)) {
      return res.status(400).json({ error: '角色只能是 admin 或 user' });
    }

    // 检查用户名是否已存在
    const [existing] = await pool.execute(
      'SELECT id FROM users WHERE username = ?',
      [username]
    );
    if (existing.length > 0) {
      return res.status(400).json({ error: '该账号已存在' });
    }

    const passwordHash = await bcrypt.hash(password, 10);
    await pool.execute(
      'INSERT INTO users (username, password_hash, role, created_by) VALUES (?, ?, ?, ?)',
      [username, passwordHash, targetRole, req.user.id]
    );

    res.status(201).json({ message: '用户创建成功' });
  } catch (err) {
    console.error('创建用户失败:', err);
    res.status(500).json({ error: '服务器内部错误' });
  }
});

// GET /api/users — 获取用户列表
router.get('/', authRequired, superAdminRequired, async (req, res) => {
  try {
    const [rows] = await pool.execute(
      'SELECT id, username, role, created_at FROM users ORDER BY id'
    );
    res.json({ users: rows });
  } catch (err) {
    console.error('获取用户列表失败:', err);
    res.status(500).json({ error: '服务器内部错误' });
  }
});

module.exports = router;

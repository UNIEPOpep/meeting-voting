// 管理员路由：密钥解锁、修改密钥
const express = require('express');
const bcrypt = require('bcryptjs');
const pool = require('../config/database');
const { authRequired, adminRequired } = require('../middleware/auth');
const { generateToken } = require('../middleware/auth');

const router = express.Router();

// POST /api/admin/unlock — 输入密钥解锁管理员权限
router.post('/unlock', authRequired, async (req, res) => {
  try {
    const { secret_key } = req.body;

    if (!secret_key) {
      return res.status(400).json({ error: '请输入密钥' });
    }

    // 从数据库获取存储的密钥
    const [rows] = await pool.execute(
      'SELECT config_value FROM system_config WHERE config_key = ?',
      ['admin_secret_key']
    );

    if (rows.length === 0) {
      return res.status(500).json({ error: '系统密钥未配置' });
    }

    const match = await bcrypt.compare(secret_key, rows[0].config_value);
    if (!match) {
      return res.status(401).json({ error: '密钥错误' });
    }

    // 获取当前用户信息
    const [userRows] = await pool.execute(
      'SELECT id, username, role FROM users WHERE id = ?',
      [req.user.id]
    );

    if (userRows.length === 0) {
      return res.status(404).json({ error: '用户不存在' });
    }

    const user = userRows[0];

    // 如果已经是管理员或超管，不需要再次升级
    if (user.role === 'admin' || user.role === 'super_admin') {
      return res.json({
        message: '已是管理员权限',
        unlocked: true,
      });
    }

    // 【修复】仅生成临时管理员令牌，不修改数据库角色
    // 退出登录后重新拿到的token还是user，权限自然失效
    const newToken = generateToken({ id: user.id, username: user.username, role: 'admin' });

    res.json({
      message: '管理员权限已解锁（本次会话有效）',
      token: newToken,
      unlocked: true,
    });
  } catch (err) {
    console.error('密钥解锁失败:', err);
    res.status(500).json({ error: '服务器内部错误' });
  }
});

// PUT /api/admin/secret-key — 修改解锁密钥（需管理员权限）
// 【修复】增加 adminRequired 中间件，普通用户无法调用
router.put('/secret-key', authRequired, adminRequired, async (req, res) => {
  try {
    const { old_key, new_key } = req.body;

    if (!old_key || !new_key) {
      return res.status(400).json({ error: '请输入旧密钥和新密钥' });
    }

    if (new_key.length < 6) {
      return res.status(400).json({ error: '新密钥至少6位' });
    }

    // 验证旧密钥
    const [rows] = await pool.execute(
      'SELECT config_value FROM system_config WHERE config_key = ?',
      ['admin_secret_key']
    );

    // 【修复】检查查询结果是否为空
    if (rows.length === 0) {
      return res.status(500).json({ error: '系统密钥未配置' });
    }

    const match = await bcrypt.compare(old_key, rows[0].config_value);
    if (!match) {
      return res.status(401).json({ error: '旧密钥错误' });
    }

    // 更新为新密钥
    const newHash = await bcrypt.hash(new_key, 10);
    await pool.execute(
      'UPDATE system_config SET config_value = ? WHERE config_key = ?',
      [newHash, 'admin_secret_key']
    );

    res.json({ message: '密钥修改成功' });
  } catch (err) {
    console.error('修改密钥失败:', err);
    res.status(500).json({ error: '服务器内部错误' });
  }
});

module.exports = router;

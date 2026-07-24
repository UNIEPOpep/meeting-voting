// 管理员路由：密钥解锁（SA/NA双密钥）、修改密钥
const express = require('express');
const bcrypt = require('bcryptjs');
const pool = require('../config/database');
const { authRequired, adminRequired } = require('../middleware/auth');
const { generateToken } = require('../middleware/auth');

const router = express.Router();

// POST /api/admin/unlock — 输入密钥解锁管理员权限
// TFSSA20241114 → super_admin / TFSNA20241114 → admin
router.post('/unlock', authRequired, async (req, res) => {
  try {
    const { secret_key } = req.body;

    if (!secret_key) {
      return res.status(400).json({ error: '请输入密钥' });
    }

    // 获取 SA 和 NA 两个密钥
    const [keys] = await pool.execute(
      'SELECT config_key, config_value FROM system_config WHERE config_key IN (?, ?)',
      ['sa_secret_key', 'na_secret_key']
    );

    if (keys.length === 0) {
      return res.status(500).json({ error: '系统密钥未配置' });
    }

    const keyMap = {};
    keys.forEach(k => { keyMap[k.config_key] = k.config_value; });

    // 先验证 SA 密钥，再验证 NA 密钥
    let targetRole = null;
    const saHash = keyMap['sa_secret_key'];
    const naHash = keyMap['na_secret_key'];

    if (saHash && await bcrypt.compare(secret_key, saHash)) {
      targetRole = 'super_admin';
    } else if (naHash && await bcrypt.compare(secret_key, naHash)) {
      targetRole = 'admin';
    } else {
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

    // 如果已经是目标角色或更高，不需要升级
    if (user.role === 'super_admin' || (user.role === 'admin' && targetRole === 'admin')) {
      return res.json({
        message: '已是管理员权限',
        unlocked: true,
      });
    }

    // 签发含目标角色的临时令牌（不修改数据库）
    const newToken = generateToken({
      id: user.id,
      username: user.username,
      role: targetRole
    });

    const roleLabel = targetRole === 'super_admin' ? '超级管理员' : '管理员';

    res.json({
      message: `${roleLabel}权限已解锁（本次会话有效）`,
      token: newToken,
      unlocked: true,
      role: targetRole,
    });
  } catch (err) {
    console.error('密钥解锁失败:', err);
    res.status(500).json({ error: '服务器内部错误' });
  }
});

// PUT /api/admin/secret-key — 修改解锁密钥（需管理员权限）
router.put('/secret-key', authRequired, adminRequired, async (req, res) => {
  try {
    const { key_type, old_key, new_key } = req.body;

    if (!key_type || !old_key || !new_key) {
      return res.status(400).json({ error: '请输入密钥类型(sa/na)、旧密钥和新密钥' });
    }

    if (!['sa', 'na'].includes(key_type)) {
      return res.status(400).json({ error: '密钥类型只能为 sa 或 na' });
    }

    if (new_key.length < 8) {
      return res.status(400).json({ error: '新密钥至少8位' });
    }

    // 只有 super_admin 能修改 SA 密钥
    if (key_type === 'sa' && req.user.role !== 'super_admin') {
      return res.status(403).json({ error: '仅超级管理员可修改SA密钥' });
    }

    const configKey = key_type === 'sa' ? 'sa_secret_key' : 'na_secret_key';

    // 验证旧密钥
    const [rows] = await pool.execute(
      'SELECT config_value FROM system_config WHERE config_key = ?',
      [configKey]
    );

    if (rows.length === 0) {
      return res.status(500).json({ error: '密钥未配置' });
    }

    const match = await bcrypt.compare(old_key, rows[0].config_value);
    if (!match) {
      return res.status(401).json({ error: '旧密钥错误' });
    }

    // 更新为新密钥
    const newHash = await bcrypt.hash(new_key, 10);
    await pool.execute(
      'UPDATE system_config SET config_value = ? WHERE config_key = ?',
      [newHash, configKey]
    );

    const label = key_type === 'sa' ? '超管' : '普管';
    res.json({ message: `${label}密钥修改成功` });
  } catch (err) {
    console.error('修改密钥失败:', err);
    res.status(500).json({ error: '服务器内部错误' });
  }
});

module.exports = router;

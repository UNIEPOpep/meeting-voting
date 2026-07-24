// 投票路由：发起投票、加入投票、提交投票、修改投票、数据汇总
const express = require('express');
const bcrypt = require('bcryptjs');
const pool = require('../config/database');
const { authRequired, adminRequired } = require('../middleware/auth');

const router = express.Router();

// 工具函数：安全解析日期，无效日期返回 null
function parseDateSafe(str) {
  if (!str || typeof str !== 'string') return null;
  const d = new Date(str);
  if (isNaN(d.getTime())) return null;
  return d;
}

// POST /api/voting-sessions — 发起投票（管理员）
router.post('/', authRequired, adminRequired, async (req, res) => {
  try {
    const { topic, file_number, vote_password, deadline, allow_abstain, allow_change_vote } = req.body;

    // 校验必填字段
    if (!topic || !file_number || !vote_password || !deadline) {
      return res.status(400).json({ error: '请填写投票主题、文件编号、投票密码和截止时间' });
    }

    // 校验截止时间是否为有效的未来时间
    const deadlineDate = parseDateSafe(deadline);
    if (!deadlineDate) {
      return res.status(400).json({ error: '截止时间格式不正确，请使用如 2026-07-30T18:00:00 的格式' });
    }
    if (deadlineDate <= new Date()) {
      return res.status(400).json({ error: '截止时间必须是将来的时间' });
    }

    // 检查文件编号是否已存在
    const [existing] = await pool.execute(
      'SELECT id FROM voting_sessions WHERE file_number = ?',
      [file_number]
    );
    if (existing.length > 0) {
      return res.status(400).json({ error: '该文件编号已被使用' });
    }

    const passwordHash = await bcrypt.hash(vote_password, 10);
    // 统一使用 !! 做布尔转换，避免 0/1 和 true/false 的混淆
    const abstain = !!allow_abstain ? 1 : 0;
    const change = !!allow_change_vote ? 1 : 0;

    const [result] = await pool.execute(
      `INSERT INTO voting_sessions (topic, file_number, vote_password_hash, deadline, allow_abstain, allow_change_vote, created_by)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [topic, file_number, passwordHash, deadline, abstain, change, req.user.id]
    );

    res.status(201).json({
      id: result.insertId,
      message: '投票创建成功',
    });
  } catch (err) {
    console.error('发起投票失败:', err);
    res.status(500).json({ error: '服务器内部错误' });
  }
});

// GET /api/voting-sessions — 获取投票列表（用于汇总）
router.get('/', authRequired, async (req, res) => {
  try {
    let sql, params;

    if (req.user.role === 'super_admin') {
      // 超管看全部
      sql = `
        SELECT vs.id, vs.topic, vs.file_number, vs.deadline, vs.allow_abstain,
               vs.allow_change_vote, vs.created_at, u.username AS created_by_name,
               (SELECT COUNT(*) FROM votes WHERE session_id = vs.id) AS vote_count
        FROM voting_sessions vs
        JOIN users u ON vs.created_by = u.id
        ORDER BY vs.created_at DESC
      `;
      params = [];
    } else if (req.user.role === 'admin') {
      // 普通管理员看自己发起的（响应结构与超管一致，包含 created_by_name）
      sql = `
        SELECT vs.id, vs.topic, vs.file_number, vs.deadline, vs.allow_abstain,
               vs.allow_change_vote, vs.created_at, u.username AS created_by_name,
               (SELECT COUNT(*) FROM votes WHERE session_id = vs.id) AS vote_count
        FROM voting_sessions vs
        JOIN users u ON vs.created_by = u.id
        WHERE vs.created_by = ?
        ORDER BY vs.created_at DESC
      `;
      params = [req.user.id];
    } else {
      // 普通用户只能看到自己参与过的投票
      sql = `
        SELECT vs.id, vs.topic, vs.file_number, vs.deadline,
               vs.allow_abstain, vs.allow_change_vote, vs.created_at
        FROM voting_sessions vs
        JOIN votes v ON vs.id = v.session_id
        WHERE v.user_id = ?
        ORDER BY vs.created_at DESC
      `;
      params = [req.user.id];
    }

    const [rows] = await pool.execute(sql, params);
    res.json({ sessions: rows });
  } catch (err) {
    console.error('获取投票列表失败:', err);
    res.status(500).json({ error: '服务器内部错误' });
  }
});

// POST /api/voting-sessions/join — 通过文件编号+密码加入投票
router.post('/join', authRequired, async (req, res) => {
  try {
    const { file_number, vote_password } = req.body;

    if (!file_number || !vote_password) {
      return res.status(400).json({ error: '请输入文件编号和投票密码' });
    }

    const [rows] = await pool.execute(
      'SELECT id, topic, file_number, vote_password_hash, deadline, allow_abstain, allow_change_vote, created_at FROM voting_sessions WHERE file_number = ?',
      [file_number]
    );

    if (rows.length === 0) {
      return res.status(404).json({ error: '文件编号或投票密码错误' });
    }

    const session = rows[0];
    const match = await bcrypt.compare(vote_password, session.vote_password_hash);
    if (!match) {
      return res.status(401).json({ error: '文件编号或投票密码错误' });
    }

    // 检查截止时间（安全解析，避免 Invalid Date 绕过）
    const deadlineDate = parseDateSafe(session.deadline);
    if (!deadlineDate || new Date() > deadlineDate) {
      return res.status(400).json({ error: '投票已截止' });
    }

    // 检查是否已投过
    const [existingVote] = await pool.execute(
      'SELECT id, choice, voted_at, updated_at FROM votes WHERE session_id = ? AND user_id = ?',
      [session.id, req.user.id]
    );

    res.json({
      session_id: session.id,
      topic: session.topic,
      file_number: session.file_number,
      deadline: session.deadline,
      allow_abstain: !!session.allow_abstain,
      allow_change_vote: !!session.allow_change_vote,
      my_vote: existingVote.length > 0 ? existingVote[0] : null,
    });
  } catch (err) {
    console.error('加入投票失败:', err);
    res.status(500).json({ error: '服务器内部错误' });
  }
});

// GET /api/voting-sessions/:id — 获取投票详情和汇总数据
router.get('/:id', authRequired, async (req, res) => {
  try {
    const sessionId = req.params.id;

    // 获取投票基本信息
    const [sessions] = await pool.execute(
      `SELECT vs.*, u.username AS created_by_name
       FROM voting_sessions vs
       JOIN users u ON vs.created_by = u.id
       WHERE vs.id = ?`,
      [sessionId]
    );

    if (sessions.length === 0) {
      return res.status(404).json({ error: '投票不存在' });
    }

    const session = sessions[0];

    // 权限检查：普通用户只能看到自己参与过的
    if (req.user.role === 'user') {
      const [myVote] = await pool.execute(
        'SELECT id FROM votes WHERE session_id = ? AND user_id = ?',
        [sessionId, req.user.id]
      );
      if (myVote.length === 0) {
        return res.status(403).json({ error: '权限不足' });
      }
    } else if (req.user.role === 'admin' && session.created_by !== req.user.id) {
      // 普通管理员只能看自己发起的
      return res.status(403).json({ error: '权限不足，只能查看自己发起的投票' });
    }

    // 汇总数据
    const [stats] = await pool.execute(
      `SELECT
         COUNT(*) AS total,
         SUM(CASE WHEN choice = 'agree' THEN 1 ELSE 0 END) AS agree_count,
         SUM(CASE WHEN choice = 'oppose' THEN 1 ELSE 0 END) AS oppose_count,
         SUM(CASE WHEN choice = 'abstain' THEN 1 ELSE 0 END) AS abstain_count
       FROM votes WHERE session_id = ?`,
      [sessionId]
    );

    // 投票明细
    const [details] = await pool.execute(
      `SELECT u.username, v.choice, v.voted_at, v.updated_at
       FROM votes v
       JOIN users u ON v.user_id = u.id
       WHERE v.session_id = ?
       ORDER BY v.voted_at`,
      [sessionId]
    );

    const s = stats[0];
    const total = s.total || 0;

    res.json({
      id: session.id,
      topic: session.topic,
      file_number: session.file_number,
      deadline: session.deadline,
      allow_abstain: !!session.allow_abstain,
      allow_change_vote: !!session.allow_change_vote,
      created_by: session.created_by_name,
      created_at: session.created_at,
      summary: {
        total,
        agree: { count: s.agree_count || 0, percent: total ? ((s.agree_count / total) * 100).toFixed(1) + '%' : '0%' },
        oppose: { count: s.oppose_count || 0, percent: total ? ((s.oppose_count / total) * 100).toFixed(1) + '%' : '0%' },
        abstain: { count: s.abstain_count || 0, percent: total ? ((s.abstain_count / total) * 100).toFixed(1) + '%' : '0%' },
      },
      details,
    });
  } catch (err) {
    console.error('获取投票详情失败:', err);
    res.status(500).json({ error: '服务器内部错误' });
  }
});

// POST /api/voting-sessions/:id/vote — 提交投票
router.post('/:id/vote', authRequired, async (req, res) => {
  try {
    const sessionId = req.params.id;
    const { choice } = req.body;

    if (!['agree', 'oppose', 'abstain'].includes(choice)) {
      return res.status(400).json({ error: '无效的投票选项' });
    }

    // 获取投票信息
    const [sessions] = await pool.execute(
      'SELECT * FROM voting_sessions WHERE id = ?',
      [sessionId]
    );
    if (sessions.length === 0) {
      return res.status(404).json({ error: '投票不存在' });
    }

    const session = sessions[0];

    // 检查截止时间（安全解析）
    const deadlineDate = parseDateSafe(session.deadline);
    if (!deadlineDate || new Date() > deadlineDate) {
      return res.status(400).json({ error: '投票已截止' });
    }

    // 检查是否不允许弃权
    if (choice === 'abstain' && !session.allow_abstain) {
      return res.status(400).json({ error: '该投票不允许弃权' });
    }

    // 使用 INSERT IGNORE 防止 TOCTOU 竞态条件：
    // 依赖数据库 UNIQUE(session_id, user_id) 约束保证唯一性，
    // 即使并发请求同时插入，也只有一个成功
    const [result] = await pool.execute(
      'INSERT IGNORE INTO votes (session_id, user_id, choice) VALUES (?, ?, ?)',
      [sessionId, req.user.id, choice]
    );

    if (result.affectedRows === 0) {
      return res.status(400).json({ error: '您已投过票了' });
    }

    res.status(201).json({ message: '投票成功' });
  } catch (err) {
    console.error('投票失败:', err);

    // 如果 INSERT IGNORE 绕不过，UNIQUE 冲突也会抛异常，兜底处理
    if (err.code === 'ER_DUP_ENTRY') {
      return res.status(400).json({ error: '您已投过票了' });
    }

    res.status(500).json({ error: '服务器内部错误' });
  }
});

// PUT /api/voting-sessions/:id/vote — 修改投票选择
router.put('/:id/vote', authRequired, async (req, res) => {
  try {
    const sessionId = req.params.id;
    const { choice } = req.body;

    if (!['agree', 'oppose', 'abstain'].includes(choice)) {
      return res.status(400).json({ error: '无效的投票选项' });
    }

    const [sessions] = await pool.execute(
      'SELECT * FROM voting_sessions WHERE id = ?',
      [sessionId]
    );
    if (sessions.length === 0) {
      return res.status(404).json({ error: '投票不存在' });
    }

    const session = sessions[0];

    // 检查截止时间（安全解析）
    const deadlineDate = parseDateSafe(session.deadline);
    if (!deadlineDate || new Date() > deadlineDate) {
      return res.status(400).json({ error: '投票已截止' });
    }

    // 检查是否允许修改
    if (!session.allow_change_vote) {
      return res.status(400).json({ error: '该投票不允许修改' });
    }

    // 检查是否不允许弃权
    if (choice === 'abstain' && !session.allow_abstain) {
      return res.status(400).json({ error: '该投票不允许弃权' });
    }

    // UPDATE 是原子操作，无需先 SELECT 再 UPDATE
    const [result] = await pool.execute(
      'UPDATE votes SET choice = ?, updated_at = NOW() WHERE session_id = ? AND user_id = ?',
      [choice, sessionId, req.user.id]
    );

    if (result.affectedRows === 0) {
      return res.status(400).json({ error: '您还未投票，无法修改' });
    }

    res.json({ message: '投票修改成功' });
  } catch (err) {
    console.error('修改投票失败:', err);
    res.status(500).json({ error: '服务器内部错误' });
  }
});

module.exports = router;

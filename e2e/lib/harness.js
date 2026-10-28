'use strict';
/**
 * 极简测试框架：按「套件 → 用例」组织，用例可写断言，跑完生成 Markdown + JSON 报告。
 * 断言失败统一抛 Error，由 test() 捕获记为 FAIL，不会中断后续用例。
 */
const fs = require('fs');
const path = require('path');

const results = [];
const startedAt = new Date();
let currentSuite = '未分组';

function suite(name) {
  currentSuite = name;
}

async function test(id, title, fn) {
  const t0 = Date.now();
  try {
    const info = await fn();
    results.push({ suite: currentSuite, id, title, ok: true, ms: Date.now() - t0, info: info == null ? '' : String(info) });
    process.stdout.write(`  \x1b[32mPASS\x1b[0m ${id} ${title}\n`);
    return true;
  } catch (e) {
    const msg = (e && e.message) ? e.message : String(e);
    results.push({ suite: currentSuite, id, title, ok: false, ms: Date.now() - t0, err: msg });
    process.stdout.write(`  \x1b[31mFAIL\x1b[0m ${id} ${title}  → ${msg}\n`);
    return false;
  }
}

function fail(msg) {
  throw new Error(msg);
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg || '断言失败');
  return true;
}

function eq(actual, expected, label) {
  if (actual !== expected) throw new Error(`${label ? label + '：' : ''}期望 [${expected}]，实际 [${actual}]`);
  return true;
}

function contains(haystack, needle, label) {
  const h = typeof haystack === 'string' ? haystack : JSON.stringify(haystack);
  if (!h.includes(needle)) throw new Error(`${label ? label + '：' : ''}期望包含 [${needle}]，实际 [${h.slice(0, 200)}]`);
  return true;
}

function notEmpty(v, label) {
  const empty = v === undefined || v === null || v === '' || (Array.isArray(v) && v.length === 0);
  if (empty) throw new Error(`${label || '值'}为空`);
  assert(true);
  return v;
}

function summary() {
  const total = results.length;
  const passed = results.filter(r => r.ok).length;
  const failed = results.filter(r => !r.ok);
  return { total, passed, failed: failed.length, failedList: failed };
}

function writeReport(reportPath, meta) {
  const s = summary();
  const duration = ((Date.now() - startedAt.getTime()) / 1000).toFixed(1);
  const suites = [...new Set(results.map(r => r.suite))];
  const lines = [];
  lines.push(`# 功能测试报告（自动生成）`);
  lines.push('');
  lines.push(`- 生成时间：${new Date().toLocaleString('zh-CN', { hour12: false })}`);
  lines.push(`- 环境：${meta.env || ''}`);
  lines.push(`- 被测入口：${meta.base || ''}`);
  lines.push(`- 用例总数：**${s.total}**，通过 **${s.passed}**，失败 **${s.failed}**，耗时 ${duration}s`);
  lines.push(`- 通过率：**${s.total ? ((s.passed / s.total) * 100).toFixed(1) : 0}%**`);
  lines.push('');
  lines.push('## 一、总览');
  lines.push('');
  lines.push('| 模块 | 用例数 | 通过 | 失败 |');
  lines.push('| --- | ---: | ---: | ---: |');
  for (const name of suites) {
    const rs = results.filter(r => r.suite === name);
    const p = rs.filter(r => r.ok).length;
    lines.push(`| ${name} | ${rs.length} | ${p} | ${rs.length - p} |`);
  }
  lines.push(`| **合计** | **${s.total}** | **${s.passed}** | **${s.failed}** |`);
  lines.push('');

  if (s.failed) {
    lines.push('## 二、失败清单（按优先级处理）');
    lines.push('');
    lines.push('| # | 用例 | 期望/现象 |');
    lines.push('| ---: | --- | --- |');
    s.failedList.forEach((r, i) => lines.push(`| ${i + 1} | ${r.id} ${r.title} | ${String(r.err).replace(/\|/g, '\\|').slice(0, 300)} |`));
    lines.push('');
  } else {
    lines.push('## 二、失败清单');
    lines.push('');
    lines.push('无失败用例。');
    lines.push('');
  }

  for (const name of suites) {
    const rs = results.filter(r => r.suite === name);
    lines.push(`## ${name}`);
    lines.push('');
    lines.push('| 用例 | 说明 | 结果 | 耗时 | 备注 |');
    lines.push('| --- | --- | --- | ---: | --- |');
    for (const r of rs) {
      const badge = r.ok ? '✅ 通过' : '❌ 失败';
      const note = r.ok ? String(r.info || '').replace(/\|/g, '\\|').slice(0, 160) : String(r.err || '').replace(/\|/g, '\\|').slice(0, 300);
      lines.push(`| ${r.id} | ${r.title} | ${badge} | ${r.ms}ms | ${note} |`);
    }
    lines.push('');
  }

  lines.push('## 说明');
  lines.push('');
  lines.push(meta.notes || '');
  lines.push('');

  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  if (meta.analysis) { lines.push('', '---', '', meta.analysis, ''); }
  fs.writeFileSync(reportPath, lines.join('\n'), 'utf8');
  fs.writeFileSync(reportPath.replace(/\.md$/, '.json'), JSON.stringify({ meta, summary: s, results }, null, 2), 'utf8');
  return s;
}

module.exports = { suite, test, fail, assert, eq, contains, notEmpty, summary, writeReport, results };

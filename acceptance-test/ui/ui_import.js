/* UI 端到端：导入按钮上传假 Excel → 结果弹窗；导出按钮下载文件。 */
const { chromium } = require('playwright-core');
const path = require('path');
const fs = require('fs');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const URL = 'http://localhost:5173/';
const FAKE = path.join(__dirname, '..', 'artifacts', 'cgdd_fake.xlsx');
const DOWNLOAD_DIR = path.join(__dirname, '..', 'artifacts');
const SHOT = path.join(__dirname, '..', 'artifacts');

const results = [];
function record(name, ok, detail = '') {
  results.push({ name, pass: !!ok, detail });
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name} ${detail}`);
}

(async () => {
  const browser = await chromium.launch({ executablePath: EDGE, headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  page.setDefaultTimeout(10000);

  await page.goto(URL, { waitUntil: 'domcontentloaded' });
  await page.getByPlaceholder('请输入账号').fill('admin');
  await page.getByPlaceholder('请输入密码').fill('admin123');
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await page.waitForSelector('.sidebar');

  // 进入采购订单
  await page.locator('.menu-group-head', { hasText: '采购管理' }).click();
  await page.locator('.menu-leaf', { hasText: '采购订单' }).click();
  await page.waitForSelector('.doc-page');
  await page.waitForSelector('.el-table');

  // ---------- 导入 ----------
  const fileInput = page.locator('.el-upload input[type=file]');
  await fileInput.setInputFiles(FAKE);
  // 等待导入结果弹窗
  await page.waitForSelector('.el-dialog', { timeout: 10000 });
  const dialogText = await page.locator('.el-dialog').first().textContent();
  record('导入弹窗显示成功单据数=10', /10成功单据/.test(dialogText)
    || /成功[^\d]*10/.test(dialogText), dialogText.replace(/\s+/g, ' ').slice(0, 120));
  record('导入弹窗含失败行 数量格式错误', dialogText.includes('数量格式错误'), '');
  record('导入弹窗含失败行 日期不能为空', dialogText.includes('日期不能为空'), '');
  await page.screenshot({ path: path.join(SHOT, 'ui_import_result.png') });

  // 关闭弹窗
  await page.locator('.el-dialog button', { hasText: '关 闭' }).click().catch(() => {});
  await page.locator('.el-dialog .el-button', { hasText: '取消' }).click().catch(() => {});
  await page.keyboard.press('Escape');
  await page.waitForTimeout(400);

  // ---------- 导出 ----------
  const dlPromise = page.waitForEvent('download', { timeout: 12000 });
  await page.locator('.toolbar button', { hasText: '导出' }).click();
  let dlName = '';
  try {
    const dl = await dlPromise;
    dlName = dl.suggestedFilename();
    await dl.saveAs(path.join(DOWNLOAD_DIR, 'ui_export_download.xlsx'));
    record('导出按钮触发下载', dlName.includes('.xlsx'), `filename=${dlName}`);
  } catch (e) {
    record('导出按钮触发下载', false, e.message);
  }
  const exported = path.join(DOWNLOAD_DIR, 'ui_export_download.xlsx');
  record('导出文件存在且非空', fs.existsSync(exported) && fs.statSync(exported).size > 1000,
    `size=${fs.existsSync(exported) ? fs.statSync(exported).size : 0}`);

  await browser.close();
  const passed = results.filter((r) => r.pass).length;
  console.log(`\n=== UI 导入/导出汇总: ${passed}/${results.length} 通过 ===`);
  process.exit(passed === results.length ? 0 : 1);
})().catch((e) => {
  console.error('UI 导入/导出异常:', e.message);
  process.exit(2);
});

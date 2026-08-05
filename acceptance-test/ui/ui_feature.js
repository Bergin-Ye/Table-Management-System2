/* 验证新功能：操作列「复制」（打开新增+回填）与「批量删除」 */
const { chromium } = require('playwright-core');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const URL = 'http://localhost:5173/';
const SHOT = __dirname + '/../artifacts/';
const results = [];

function record(name, ok, detail = '') {
  results.push({ name, pass: !!ok, detail });
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name} ${detail}`);
}

(async () => {
  const browser = await chromium.launch({ executablePath: EDGE, headless: true });
  const page = await browser.newPage({ viewport: { width: 1500, height: 900 } });
  page.setDefaultTimeout(10000);

  // 登录 → 采购订单
  await page.goto(URL, { waitUntil: 'domcontentloaded' });
  await page.getByPlaceholder('请输入账号').fill('admin');
  await page.getByPlaceholder('请输入密码').fill('admin123');
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await page.waitForSelector('.sidebar');
  await page.locator('.menu-group-head', { hasText: '采购管理' }).click();
  await page.locator('.menu-leaf', { hasText: '采购订单' }).click();
  await page.waitForSelector('.doc-page');
  await page.waitForSelector('.el-table');
  await page.waitForTimeout(500);

  // 1. 批量删除按钮存在且默认禁用
  const batchBtn = page.locator('.toolbar button', { hasText: '批量删除' });
  record('工具栏含「批量删除」按钮', (await batchBtn.count()) > 0);
  record('批量删除按钮初始为禁用', await batchBtn.isDisabled());
  record('表格含勾选列', (await page.locator('.el-table th .el-checkbox').count()) > 0);

  // 2. 复制按钮：点击 FEA-001 行的复制
  const row001 = page.locator('.el-table__row', { hasText: 'FEA-001' }).first();
  record('操作列含「复制」按钮', (await row001.getByRole('button', { name: '复制' }).count()) > 0);
  await row001.getByRole('button', { name: '复制' }).click();
  await page.waitForSelector('.doc-edit-dialog', { timeout: 8000 });

  // 3. 复制弹窗验证：标题、回填、编号清空
  const title = await page.locator('.doc-edit-dialog .el-dialog__title').textContent();
  record('复制弹窗标题=复制新增采购订单', String(title).includes('复制新增采购订单'), `title=${title}`);
  const supplierVal = await page.locator('.doc-edit-dialog input[placeholder="请输入供应商"]').inputValue();
  record('复制回填供应商值', supplierVal === '特性测试供应商1', `supplier=${supplierVal}`);
  const bizNoVal = await page.locator('.doc-edit-dialog input[placeholder="请输入编号"]').inputValue();
  record('复制后编号被清空(引导填新号)', bizNoVal === '', `bizNo='${bizNoVal}'`);
  const detailRows = await page.locator('.doc-edit-dialog .detail-wrap .el-table__row').count();
  record('复制回填明细行数=2', detailRows === 2, `rows=${detailRows}`);
  await page.screenshot({ path: SHOT + 'feature_copy_dialog.png' });

  // 4. 填新编号并保存
  await page.locator('.doc-edit-dialog input[placeholder="请输入编号"]').fill('FEA-COPY-1');
  await page.locator('.doc-edit-dialog button', { hasText: '保 存' }).click();
  await page.waitForTimeout(800);
  const saved = await page.locator('.el-message').allTextContents();
  record('复制保存成功提示', saved.some((m) => m.includes('保存成功')), JSON.stringify(saved));
  await page.waitForTimeout(500);
  const listText = await page.locator('.el-table').textContent();
  record('新单据 FEA-COPY-1 出现在列表', String(listText).includes('FEA-COPY-1'));

  // 5. 批量删除：勾选 FEA-002 与 FEA-003
  for (const code of ['FEA-002', 'FEA-003']) {
    await page.locator('.el-table__row', { hasText: code }).first()
      .locator('.el-checkbox').click();
  }
  await page.waitForTimeout(200);
  record('选中后批量删除按钮可用', !(await batchBtn.isDisabled()));
  await batchBtn.click();
  await page.waitForSelector('.el-message-box', { timeout: 5000 });
  const confirmText = await page.locator('.el-message-box').textContent();
  record('批量删除确认框提示数量', String(confirmText).includes('2'), String(confirmText).replace(/\s+/g, ' ').slice(0, 60));
  await page.locator('.el-message-box button', { hasText: '删除' }).click();
  await page.waitForTimeout(1200);
  const afterDel = await page.locator('.el-table').textContent();
  record('FEA-002/FEA-003 已删除', !String(afterDel).includes('FEA-002') && !String(afterDel).includes('FEA-003'));
  record('FEA-001 与 FEA-COPY-1 保留', String(afterDel).includes('FEA-001') && String(afterDel).includes('FEA-COPY-1'));
  await page.screenshot({ path: SHOT + 'feature_batch_delete.png' });

  await browser.close();
  const passed = results.filter((r) => r.pass).length;
  console.log(`\n=== 新功能验证汇总: ${passed}/${results.length} 通过 ===`);
  process.exit(passed === results.length ? 0 : 1);
})().catch((e) => {
  console.error('新功能验证异常:', e.message);
  process.exit(2);
});

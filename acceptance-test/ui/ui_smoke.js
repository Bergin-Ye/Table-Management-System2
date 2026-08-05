/* UI 冒烟测试：登录/登出、菜单展开收起、单据页加载、列设置保存、RBAC 菜单过滤 */
const { chromium } = require('playwright-core');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const URL = 'http://localhost:5173/';
const SHOT = __dirname + '/../artifacts/';

const results = [];
function record(name, ok, detail = '') {
  results.push({ name, pass: !!ok, detail });
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name} ${detail}`);
}

async function login(page, username, password) {
  await page.goto(URL, { waitUntil: 'domcontentloaded' });
  await page.getByPlaceholder('请输入账号').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByRole('button', { name: /登\s*录/ }).click();
  // 等待登录成功进入主布局
  await page.waitForSelector('.sidebar', { timeout: 8000 });
}

(async () => {
  const browser = await chromium.launch({ executablePath: EDGE, headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.setDefaultTimeout(8000);
  page.on('pageerror', (e) => console.log('PAGEERROR:', e.message));

  // ---------- 1. admin 登录 ----------
  await login(page, 'admin', 'admin123');
  record('admin 登录成功进入主布局', await page.locator('.sidebar').isVisible());

  // 顶部用户信息
  const userChip = await page.locator('.user-chip .nickname').first().textContent();
  record('顶部显示昵称(系统管理员)', String(userChip).includes('系统管理员'), `nick=${userChip}`);

  // ---------- 2. 菜单树 + 展开/收起 ----------
  const groups = await page.locator('.menu-group-head .menu-group-name').allTextContents();
  record('admin 菜单含 4 个分组', JSON.stringify(groups).includes('系统管理')
    && groups.length === 4, `groups=${JSON.stringify(groups)}`);

  // 采购管理初始可能收起（localStorage 为空）
  const purchaseSub = page.locator('.menu-group', { hasText: '采购管理' }).locator('.menu-sub');
  const subVisibleBefore = await purchaseSub.isVisible().catch(() => false);
  // 点击展开
  await page.locator('.menu-group-head', { hasText: '采购管理' }).click();
  await page.waitForTimeout(350);
  const subVisibleAfter = await purchaseSub.isVisible().catch(() => false);
  const children = await purchaseSub.locator('.menu-leaf-name').allTextContents();
  record('点击上级展开子菜单', !subVisibleBefore && subVisibleAfter,
    `before=${subVisibleBefore} after=${subVisibleAfter} children=${JSON.stringify(children)}`);
  record('子菜单含 采购申请/采购订单', children.includes('采购申请') && children.includes('采购订单'),
    JSON.stringify(children));
  // 再次点击收起
  await page.locator('.menu-group-head', { hasText: '采购管理' }).click();
  await page.waitForTimeout(350);
  const subAfterCollapse = await purchaseSub.isVisible().catch(() => false);
  record('再次点击收起子菜单', !subAfterCollapse, `visible=${subAfterCollapse}`);

  // ---------- 3. 进入单据页 ----------
  await page.locator('.menu-group-head', { hasText: '采购管理' }).click();
  await page.locator('.menu-leaf', { hasText: '采购订单' }).click();
  await page.waitForSelector('.doc-page', { timeout: 8000 });
  await page.waitForSelector('.el-table', { timeout: 8000 });
  record('进入采购订单页, 表格加载', await page.locator('.el-table').isVisible());
  record('页标题为采购订单', (await page.locator('.panel-title .title-text').first().textContent()).includes('采购订单'));
  const toolbarBtns = await page.locator('.toolbar button').allTextContents();
  record('工具栏含 导入/导出/列设置/新增单据',
    ['导入', '导出', '列设置', '新增单据'].every((t) => toolbarBtns.some((b) => b.includes(t))),
    JSON.stringify(toolbarBtns));
  await page.screenshot({ path: SHOT + 'ui_docpage_cgdd.png', fullPage: false });

  // ---------- 4. 列设置 ----------
  await page.locator('button', { hasText: '列设置' }).click();
  await page.waitForSelector('.column-selector', { timeout: 5000 });
  record('列设置弹窗打开', await page.locator('.column-selector').isVisible());
  const colsBefore = await page.locator('.column-selector .col-item').count();
  // 取消一个勾选
  await page.locator('.column-selector .col-item').nth(0).click();
  await page.locator('.column-selector button', { hasText: '保 存' }).click();
  // 等待“列偏好已保存”消息出现（排除登录时残留的旧 toast）
  let toastSaved = false;
  for (let i = 0; i < 10; i++) {
    const msgs = await page.locator('.el-message').allTextContents().catch(() => []);
    if (msgs.some((m) => m.includes('列偏好已保存'))) { toastSaved = true; break; }
    await page.waitForTimeout(300);
  }
  record('保存列偏好出现成功提示', toastSaved, `toastSaved=${toastSaved}`);
  record('列选择器勾选项数量>0', colsBefore > 0, `count=${colsBefore}`);
  await page.screenshot({ path: SHOT + 'ui_column_saved.png' });

  // ---------- 5. 登出 ----------
  await page.locator('.icon-btn').click();
  await page.locator('.el-message-box button', { hasText: '退出' }).click();
  await page.waitForSelector('.login-shell', { timeout: 6000 });
  record('admin 登出回到登录页', await page.locator('.login-shell').isVisible());

  // ---------- 6. user1 登录：无系统管理 ----------
  await login(page, 'user1', '123456');
  const groups1 = await page.locator('.menu-group-head .menu-group-name').allTextContents();
  record('user1 登录菜单含 3 个分组', groups1.length === 3, `groups=${JSON.stringify(groups1)}`);
  record('user1 菜单不含系统管理', !JSON.stringify(groups1).includes('系统管理'));
  await page.screenshot({ path: SHOT + 'ui_user1_menu.png' });

  // ---------- 7. 明细分页/新增弹窗（采购订单打开新增） ----------
  await page.locator('.menu-group-head', { hasText: '采购管理' }).click();
  await page.locator('.menu-leaf', { hasText: '采购订单' }).click();
  await page.waitForSelector('.doc-page');
  await page.locator('button', { hasText: '新增单据' }).click();
  await page.waitForSelector('.el-dialog', { timeout: 6000 });
  const dialogVisible = await page.locator('.el-dialog').first().isVisible().catch(() => false);
  record('新增单据弹窗打开(头部+明细表单)', dialogVisible);
  await page.screenshot({ path: SHOT + 'ui_doc_edit_dialog.png' });

  await browser.close();
  const passed = results.filter((r) => r.pass).length;
  console.log(`\n=== UI 冒烟汇总: ${passed}/${results.length} 通过 ===`);
  process.exit(passed === results.length ? 0 : 1);
})().catch((e) => {
  console.error('UI 测试异常:', e.message);
  process.exit(2);
});

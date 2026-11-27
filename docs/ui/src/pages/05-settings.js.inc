    const localBackupScenarios={
      normal:['自动备份正常','Documents/KitsuneBackup'],
      checking:['正在检查备份完整性','Documents/KitsuneBackup'],
      failed:['无法访问备份目录，请检查目录是否可用或重试','Documents/KitsuneBackup'],
      reauth:['备份目录授权已失效，请重新授权','Documents/KitsuneBackup'],
      unconfigured:['未配置',''],
    };
    const localBackupCandidates=[
      {time:'2026-11-18 09:30:15 (+08:00)',size:'18.4 KB'},
      {time:'2026-11-17 21:12:04 (+08:00)',size:'17.9 KB'},
    ];
    function pageLocalBackup(){
      const demo=state.localBackupDemo,sc=localBackupScenarios[demo.scenario];
      const configured=demo.scenario!=='unconfigured',reauth=demo.scenario==='reauth',failed=demo.scenario==='failed';
      return `<div class="ks-form">
        ${section('自动备份')}
        <section class="ks-money-card ks-form">
          <div role="status">
            <h3 class="ks-backup-status" ${failed||reauth?'style="color:var(--ks-danger)"':''}>${sc[0]}</h3>
            ${configured?'<p class="ks-note">上次成功 · 2026-11-18 09:30:15 (+08:00)</p>':''}
            ${configured?`<p class="ks-note">备份目录 · ${escapeText(sc[1])}</p>`:'<p class="ks-note">授权本地目录后，数据变更会自动备份并在进入页面时校验完整性。</p>'}
          </div>
          ${!configured?'<button type="button" class="ks-primary ks-full cursor-interaction" data-local-backup-action="configure">设置目录</button>'
            :reauth?'<button type="button" class="ks-primary ks-full cursor-interaction" data-local-backup-action="configure">重新授权目录</button>'
            :failed?'<div class="ks-choices"><button type="button" class="ks-primary cursor-interaction" style="flex:1" data-local-backup-action="retry">重试</button><button type="button" class="ks-choice cursor-interaction" style="flex:1" data-local-backup-action="configure">更改目录</button></div>'
            :'<button type="button" class="ks-choice ks-full cursor-interaction" data-local-backup-action="configure">更改目录</button>'}
        </section>
        ${section('备份与恢复')}
        <div class="ks-settings">
          <button type="button" class="ks-setting ks-setting-link cursor-interaction" data-local-backup-action="manual">${icon('files')}<span>手动备份<small class="ks-note" style="display:block">导出完整 JSON 到自选位置，不参与自动清理</small></span>${icon('chevron-right')}</button>
          <button type="button" class="ks-setting ks-setting-link cursor-interaction" data-local-backup-action="open-restore">${icon('undo-2')}<span>恢复备份<small class="ks-note" style="display:block">从自动备份候选或本地 JSON 文件恢复</small></span>${icon('chevron-right')}</button>
        </div>
        <p class="ks-note">完整备份包含所有业务数据和外观。文件为可读 JSON，不含凭据。</p>
        <label class="ks-footnote">交互预览 · 自动备份状态
          <select data-local-backup-scenario aria-label="本地自动备份示例状态">
            ${[['normal','自动备份正常'],['checking','正在检查完整性'],['failed','目录暂时无法访问'],['reauth','目录授权异常'],['unconfigured','未配置目录']].map(([v,l])=>`<option value="${v}" ${demo.scenario===v?'selected':''}>${l}</option>`).join('')}
          </select>
        </label>
      </div>`;
    }
    function localRestoreDialog(){
      const demo=state.localBackupDemo,ok=demo.scenario==='normal'||demo.scenario==='checking';
      const body=demo.scenario==='reauth'?'<p class="ks-note">自动备份目录授权异常，无法读取目录中的备份。仍可选择其他本地备份文件。</p>'
        :demo.scenario==='unconfigured'?'<p class="ks-note">尚未配置自动备份目录，可直接选择本地备份文件。</p>'
        :demo.scenario==='failed'?'<p class="ks-error">无法读取已授权目录中的自动备份</p>'
        :`<p class="ks-note">已授权目录中的自动备份（按时间倒序）</p><div class="ks-form">${localBackupCandidates.map(c=>`<button type="button" class="ks-choice ks-full cursor-interaction" style="justify-content:space-between" data-local-backup-action="pick-candidate"><span><strong>${c.time}</strong><small class="ks-note" style="display:block;text-align:left">${c.size}</small></span>${icon('chevron-right')}</button>`).join('')}</div>`;
      return `<div class="ks-modal-overlay ks-delete-overlay"><section class="ks-delete-sheet" role="alertdialog" aria-modal="true" aria-label="恢复备份">
        <h3>恢复备份</h3>
        ${body}
        <button type="button" class="ks-choice ks-full cursor-interaction" data-local-backup-action="pick-file">${icon('files')}选择其他备份文件</button>
        <div class="ks-period-actions"><button type="button" class="ks-text-btn cursor-interaction" data-local-backup-action="close-restore">取消</button></div>
      </section></div>`;
    }
    function pageCsvTransfer(){
      const demo=state.csvDemo,meals=demo.kind==='MEALS',label=meals?'用餐':'记账';
      const guide=meals
        ?`<p class="ks-note">表头：id, date, meal_slot, planned_at_home, meal_method, preparation_categories, dishes, note</p><p class="ks-note">日期：YYYY-MM-DD；餐次：BREAKFAST / LUNCH / DINNER；计划在家：true / false（必填）。</p>`
        :`<p class="ks-note">表头：id, date, time, type, wallet, currency, amount, category, to_wallet, to_currency, to_amount, original_expense_id, note</p><p class="ks-note">日期：YYYY-MM-DD；时间：24 小时制 HH:mm（必填）；类型：INCOME / EXPENSE / TRANSFER / REFUND。</p>`;
      return `<div class="ks-tabs" role="tablist" aria-label="CSV 类型">
        ${[['MEALS','用餐'],['LEDGER','记账']].map(([v,l])=>`<button type="button" role="tab" class="cursor-interaction" data-csv-kind="${v}" aria-selected="${demo.kind===v}">${l}</button>`).join('')}
      </div>
      <div class="ks-form">
        ${section('导入')}
        <section class="ks-money-card ks-form">
          <button type="button" class="ks-primary ks-full cursor-interaction" data-csv-action="preview">选择 CSV 文件</button>
          <button type="button" class="ks-choice ks-full cursor-interaction" data-csv-action="template">导出 CSV 模板</button>
          <button type="button" class="ks-setting ks-setting-link cursor-interaction" data-csv-action="toggle-guide"><span>CSV 格式说明</span><small class="ks-note">${demo.guideOpen?'收起':'展开'}</small>${icon('chevron-down')}</button>
          ${demo.guideOpen?`<div class="ks-form">${guide}</div>`:''}
        </section>
        ${section('导出')}
        <section class="ks-money-card ks-form">
          <p class="ks-note">导出全部${label}记录为 UTF-8 CSV 文件，保留稳定记录 ID。</p>
          <button type="button" class="ks-choice ks-full cursor-interaction" data-csv-action="export">导出全部${label}记录</button>
        </section>
      </div>`;
    }
    function csvPreviewDialog(){
      const demo=state.csvDemo,meals=demo.kind==='MEALS',wallet=wallets.find(w=>w.id===state.csvWallet);
      const canCommit=demo.additions>0&&demo.errors===0;
      return `<div class="ks-modal-overlay ks-delete-overlay"><section class="ks-delete-sheet" role="alertdialog" aria-modal="true" aria-label="${meals?'用餐':'记账'} CSV 导入预览">
        <h3>${meals?'用餐':'记账'} CSV 导入预览</h3>
        <div class="ks-choices">
          <div class="ks-money-card" style="flex:1;text-align:center;padding:8px"><span class="ks-note">新增</span><strong style="display:block">${demo.additions}</strong></div>
          <div class="ks-money-card" style="flex:1;text-align:center;padding:8px"><span class="ks-note">重复</span><strong style="display:block">${demo.duplicates}</strong></div>
          <div class="ks-money-card" style="flex:1;text-align:center;padding:8px"><span class="ks-note">错误</span><strong style="display:block;${demo.errors?'color:var(--ks-danger)':''}">${demo.errors}</strong></div>
        </div>
        ${meals?selectionControl('csv-method','映射 外送',state.csvMethod):selectionControl('csv-wallet','映射 随身账户 · CNY',wallet?.name)}
        <div class="ks-period-actions">
          <button type="button" class="ks-text-btn cursor-interaction" data-csv-action="close-preview">取消</button>
          <button type="button" class="ks-primary cursor-interaction" data-csv-action="commit" ${canCommit?'':'disabled'}>确认导入（${demo.additions} 条）</button>
        </div>
      </section></div>`;
    }
    function pageSettings(){
      const item=(glyph,label,attrs='',detail='')=>`<${attrs?'button type="button"':'div'} class="ks-setting ${attrs?'ks-setting-link cursor-interaction':''}" ${attrs}>${icon(glyph)}<span>${label}</span>${detail}${icon('chevron-right')}</${attrs?'button':'div'}>`;
      return `${section('分类管理')}<div class="ks-settings">
        ${item('utensils','用餐方式')}
        ${item('cooking-pot','制作分类')}
        ${item('tags','通用收支分类','data-action="manage-categories" data-category-wallet="GLOBAL"')}
        ${item('wallet','钱包专属分类','data-action="manage-categories" data-category-wallet="daily"')}
      </div>
      ${section('个性化')}<div class="ks-settings">${item('palette','外观与调色板','data-action="open-appearance"')}</div>
      ${section('数据与备份')}<div class="ks-settings">
        ${item('hard-drive','本地备份与恢复','data-action="open-local-backup"')}
        ${item('cloud','GitHub 备份','data-github-action="open"',githubPause()
          ? '<small class="ks-backup-warning"><span aria-hidden="true">⚠</span> 自动备份已暂停</small>' : '')}
        ${item('files','CSV 导入与导出','data-action="open-csv-transfer"')}
      </div>`;
    }
    function pageAppearance(){
      const draft=state.appearanceDraft;
      return `<div class="ks-settings">
        <label class="ks-setting">${icon('sun-moon')}<span>明暗模式</span><select data-theme aria-label="明暗模式">${themeModes.map(([value,label])=>`<option value="${value}" ${draft.mode===value?'selected':''}>${label}</option>`).join('')}</select></label>
        <label class="ks-setting">${icon('palette')}<span>配色来源</span><select data-color-source aria-label="配色来源">${[['PRESET','预设调色板'],['CUSTOM','自选主色'],['SYSTEM','跟随系统壁纸']].map(([v,n])=>`<option value="${v}" ${draft.colorSource===v?'selected':''}>${n}</option>`).join('')}</select></label>
        ${draft.colorSource==='PRESET'?`<label class="ks-setting">${icon('swatch-book')}<span>调色板</span><select data-palette aria-label="调色板">${[['#c47a4a','杏橙'],['#5f7f64','茶绿'],['#587c9b','雾蓝'],['#8a729f','薰衣草'],['#a76f82','豆沙粉']].map(([v,n])=>`<option value="${v}" ${draft.presetColor===v?'selected':''}>${n}</option>`).join('')}</select></label>`:''}
        ${draft.colorSource==='CUSTOM'?`<label class="ks-custom-color-row"><input type="color" data-custom-picker aria-label="选择主色" value="${normalizeHex(draft.customColor)||style.customColor}"><input type="text" data-custom-hex aria-label="主色 HEX" value="${escapeText(draft.customColor)}" placeholder="#C47A4A" maxlength="7" spellcheck="false"></label>`:''}
        ${draft.colorSource==='SYSTEM'?`<div class="ks-setting">${icon('sparkles')}<span>使用系统壁纸配色</span><small>Android 12+</small></div>`:''}
      </div>
      <button type="button" class="ks-text-btn cursor-interaction" data-action="reset-appearance">恢复默认</button>
      <div data-appearance-editor hidden>
        ${draft.colorSource==='SYSTEM'?'<p class="ks-appearance-note">此处使用示意色板，真实配色由手机系统提供。</p>':''}
        <div class="ks-preview-modes" role="group" aria-label="预览明暗"><span class="ks-note">预览明暗</span><div class="ks-choices">${themeModes.slice(1).map(([value,label])=>choice(label,value,null,'preview-mode')).join('')}</div></div>
        <div class="ks-appearance-preview" data-appearance-preview aria-label="配色预览"><div class="ks-record">${recordIcon('sunrise')}<span class="ks-record-copy"><span class="ks-record-title">早餐 <span class="ks-pill">下厨</span></span><span class="ks-record-meta">燕麦粥、煎蛋</span></span></div><div class="ks-record">${recordIcon('receipt-text')}<span class="ks-record-copy"><span class="ks-record-title">工资</span><span class="ks-record-meta">收入</span></span><span class="ks-currency-amount ks-income"><strong>+100.00</strong><small>CNY</small></span></div><div class="ks-record">${recordIcon('receipt-text')}<span class="ks-record-copy"><span class="ks-record-title">餐饮</span><span class="ks-record-meta">支出</span></span><span class="ks-currency-amount ks-expense"><strong>−28.00</strong><small>CNY</small></span></div><span class="ks-primary ks-full" aria-hidden="true">保存</span></div>
        <p class="ks-error" data-appearance-error role="alert"></p><div class="ks-appearance-actions"><button type="button" class="ks-choice cursor-interaction" data-action="cancel-appearance">取消</button><button type="button" class="ks-primary cursor-interaction" data-action="apply-appearance">应用外观</button></div>
      </div>
      `;
    }
    function pageCategoryManager(){
      const wallet=state.categoryWalletId==='GLOBAL'?null:wallets.find(w=>w.id===state.categoryWalletId);
      const scope=state.financialScope,mode=scopeMode(wallet,scope);
      const own=wallet?walletFinancialCategories[wallet.id][scope]:sharedFinancialCategories[scope];
      const editable=!wallet||mode==='CUSTOM';
      const shown=editable?own:sharedFinancialCategories[scope];
      const name=wallet?wallet.name:'通用收支分类';
      return `<div class="ks-category-manager">
        <p class="ks-note">钱包没有设置专属分类时，自动使用通用分类；收入与支出分别设置。</p>
        ${selectionControl('category-wallet','管理对象',wallet?`${wallet.name} · ${wallet.code}`:'通用分类')}
        <fieldset><legend class="ks-label">收支类型</legend><div class="ks-choices">
          ${[['EXPENSE','支出'],['INCOME','收入']].map(([code,label])=>`<button type="button" class="ks-choice cursor-interaction" data-financial-scope="${code}" aria-pressed="${scope===code}">${label}</button>`).join('')}
        </div></fieldset>
        ${wallet?`<label><span class="ks-label">${escapeText(name)} · ${scope==='EXPENSE'?'支出':'收入'}分类来源</span>
          <select class="ks-control" data-category-mode><option value="DEFAULT" ${mode==='DEFAULT'?'selected':''}>使用通用分类</option><option value="CUSTOM" ${mode==='CUSTOM'?'selected':''}>使用本钱包专属分类</option></select>
        </label>`:''}
        <p class="ks-note">${wallet?(mode==='DEFAULT'?'使用通用分类。切为专属后，不会与通用列表混合。':'使用本钱包专属分类；即使没有自定义项，也只保留“未分类”。'):'通用类别供未开启专属分类的钱包使用。'}</p>
        ${section((wallet?escapeText(name)+' · ':'')+(scope==='EXPENSE'?'支出':'收入')+'分类')}
        ${financialCategoryRows(shown,editable,true)}
        ${editable?newFinancialCategoryButton():`<button type="button" class="ks-choice ks-full" data-action="edit-owned-categories">管理本钱包专属分类</button>`}
      </div>`;
    }
    function financialCategoryRows(categories,editable,showFallback){
      return `<div class="ks-settings ks-category-rows">
        ${categories.map(c=>{
          const label=`${categoryGlyph(c.icon)}<span>${escapeText(c.name)}${c.archived?'<small class="ks-note"> · 已停用</small>':''}</span>`;
          return editable?`<button type="button" class="ks-setting ks-setting-link cursor-interaction" data-edit-financial-category="${c.id}">${label}${icon('chevron-right')}</button>`:
            `<div class="ks-setting">${label}<small>通用</small></div>`;
        }).join('')}
        ${showFallback?`<div class="ks-setting">${categoryGlyph()}<span>未分类 <small class="ks-note">· 固定兜底</small></span><small>所有钱包可用</small></div>`:''}
      </div>`;
    }
    function newFinancialCategoryButton(){
      return `<button type="button" class="ks-primary ks-full" data-edit-financial-category="new">${icon('plus')}新增分类</button>`;
    }
    function pageOwnedCategories(){
      const categories=walletFinancialCategories[state.categoryWalletId][state.financialScope];
      return `<div class="ks-category-manager">${categories.length?financialCategoryRows(categories,true,false):'<p class="ks-note">暂无专属分类</p>'}${newFinancialCategoryButton()}</div>`;
    }
    function pageCategoryDetail(){
      const current=state.editingFinancialCategoryId?categoryById(state.editingFinancialCategoryId):null;
      const owner=state.categoryWalletId,scope=state.financialScope;
      const categories=owner==='GLOBAL'?sharedFinancialCategories[scope]:walletFinancialCategories[owner][scope];
      return `<form class="ks-form ks-category-form" data-financial-category-form>
        <label><span class="ks-label">名称</span><input name="categoryName" class="ks-control" value="${escapeText(current?.name||'')}" placeholder="输入名称" maxlength="30" required></label>
        ${categoryIconPicker(current?.icon)}
        <label><span class="ks-label">排序（数字越小越靠前）</span><input name="sortOrder" class="ks-control" type="number" min="0" max="2147483647" step="1" required value="${current?.sortOrder??nextCategoryOrder(categories)}"></label>
        <label class="ks-wallet-check"><input name="archived" type="checkbox" ${current?.archived?'checked':''}>停用</label>
        ${formFooter(saveButton('保存'),'交互预览 · 使用示例分类')}
      </form>`;
    }
    const githubScenarios = {
      normal: ['自动备份正常'],
      wifi: ['等待 Wi-Fi'],
      pending: ['等待自动备份'],
      retry: ['等待重试'],
      running: ['正在备份'],
      review: ['新连接待核对','需要确认当前远端版本后才能开始自动备份。','确认 GitHub 备份'],
      conflict: ['远端版本变化','GitHub 备份发生变化，需要确认后才能继续自动备份。','处理备份冲突'],
      missing: ['远端文件已删除','GitHub 备份发生变化，需要确认后才能继续自动备份。','处理备份冲突'],
      connection: ['连接异常','GitHub Token 已失效，请重新配置。','重新配置连接'],
      restored: ['完整恢复待授权','完整恢复已完成，需要授权后才能更新 GitHub 备份。','确认更新 GitHub 备份'],
    };
    const githubPause = () => githubScenarios[state.githubDemo.scenario][1];
    const githubLocalSaved = '本机记录仍会正常保存，暂时不会自动上传。';
    const githubAction = (label,action,kind='choice',extra='') =>
      `<button type="button" class="ks-${kind} ${extra}" data-github-action="${action}">${label}</button>`;
    function pageGitHubBackup(){
      const demo=state.githubDemo,pause=githubPause(),scenario=githubScenarios[demo.scenario];
      return `<section class="ks-money-card ks-form">
        <div role="status">
          <h3 class="ks-backup-status">${pause?'自动备份已暂停':demo.uploading?'正在备份':scenario[0]}</h3>
          ${pause?`<p>${pause}</p><p class="ks-note">${githubLocalSaved}</p>`:''}
          ${pause&&demo.uploading?'<p class="ks-note">正在备份</p>':''}
          <p class="ks-note">上次成功 · 2026-11-17 ${demo.backedUp?'09:41':'09:30'}</p>
          <p class="ks-note">demo/kitsune-backup · main</p>
        </div>
        ${pause?githubAction(scenario[2],demo.scenario==='connection'?'configure':'resolve','primary','ks-full'):''}
      </section>
      ${demo.editing?`<section class="ks-money-card ks-form">
        <h3 class="ks-backup-status">连接私有仓库</h3>
        <label><span class="ks-label">仓库（owner/repository）</span><input class="ks-control" value="demo/kitsune-backup"></label>
        <label><span class="ks-label">分支</span><input class="ks-control" value="main"></label>
        <label><span class="ks-label">细粒度访问令牌</span><input class="ks-control" type="password" disabled></label>
        <label class="ks-setting"><span>仅使用 Wi-Fi</span><input type="checkbox" role="switch" data-github-wifi-draft ${demo.draftWifi?'checked':''}></label>
        <p class="ks-note">请将令牌限定为数据仓库的 Contents 读写权限。完整 JSON 会保存在 backups/latest.json。</p>
        ${githubAction('测试并保存连接','save-connection','primary','ks-full')}
        ${githubAction('取消','cancel-connection','choice','ks-full')}
      </section>`:''}
      <section class="ks-money-card ks-form">
        <button type="button" class="ks-primary ks-full" data-github-action="backup" ${pause||demo.uploading||demo.scenario==='running'?'disabled':''}>立即备份</button>
        ${githubAction('从 GitHub 恢复','restore-preview','choice','ks-full')}
      </section>
      <section class="ks-money-card ks-form">
        <label class="ks-setting"><span>仅使用 Wi-Fi</span><input type="checkbox" role="switch" data-github-wifi ${demo.wifi?'checked':''} ${demo.editing?'disabled':''}></label>
        ${demo.scenario!=='connection'?githubAction('重新配置连接','configure','choice','ks-full'):''}
      </section>
      <label class="ks-footnote">交互预览 · 示例状态
        <select data-github-scenario aria-label="GitHub 示例状态">
          ${Object.entries(githubScenarios).map(([value,[label]])=>`<option value="${value}" ${demo.scenario===value?'selected':''}>${label}</option>`).join('')}
        </select>
      </label>`;
    }
    function githubDemoDialog(){
      const demo=state.githubDemo, step=demo.review, missing=demo.scenario==='missing';
      const title={
        'restore-preview':'从 GitHub 恢复', resolve:githubScenarios[demo.scenario][2],
        restore:'确认完整恢复', upload:missing?'重新创建 GitHub 备份':'保留本机数据并覆盖 GitHub',
        notice:'GitHub 自动备份已暂停',
      }[step];
      const summary = `<p class="ks-delete-summary">本机 · 用餐 2 条 · 钱包 3 个 · 账目 11 笔</p>
        ${missing?'<p>GitHub 尚无备份</p>':`<p class="ks-delete-summary">GitHub 备份时间 · 2026-11-17 09:30
用餐 2 条 · 钱包 3 个 · 账目 10 笔</p>
          ${step==='resolve'&&demo.scenario==='restored'?'':'<p class="ks-note">恢复会替换本机全部业务数据与外观。继续恢复前需写入并校验本机保护文件。</p>'}`}`;
      let body=summary, primary='', cancel='取消';
      if(step==='notice'){
        body=`<p>${githubPause()}</p><p class="ks-note">${githubLocalSaved}</p>`;
        primary=githubAction('前往处理','handle','primary');cancel='稍后处理';
      }else if(step==='restore-preview'){
        if(missing)cancel='返回';
        else primary=githubAction('继续恢复','restore','primary');
      }else if(step==='resolve'){
        body+=`<div class="ks-form">${!missing&&demo.scenario!=='restored'
          ?githubAction('使用 GitHub 数据恢复本机','restore','choice','ks-full'):''}
          ${githubAction(missing?'重新创建 GitHub 备份':'保留本机数据并覆盖 GitHub','upload','choice','ks-full')}</div>`;
      }else if(step==='restore'){
        body='<p>恢复会替换本机全部业务数据与外观。</p><p class="ks-note">恢复前需写入并校验本机保护文件。</p>';
        primary=githubAction('导出保护文件并恢复','apply-restore','primary');
      }else if(step==='upload'){
        body=`<p>${missing?'这会将本机完整数据写入 GitHub，建立新的备份文件。':'这会用本机完整数据覆盖当前 GitHub 备份。'}</p>
          <p class="ks-note">确认仅适用于本次核对的仓库、分支和远端版本。</p>`;
        primary=githubAction(missing?'确认备份':'确认覆盖 GitHub','apply-upload','primary',missing?'':'ks-danger-confirm');
      }
      return `<div class="ks-modal-overlay ks-delete-overlay"><section class="ks-delete-sheet" role="alertdialog" aria-modal="true" aria-label="${title}">
        <h3>${title}</h3>${body}
        <div class="ks-period-actions">${githubAction(cancel,'cancel','text-btn')}${primary}</div>
      </section></div>`;
    }

    function pageSettings(){
      const draft=state.appearanceDraft;
      return `${section('外观')}<div class="ks-settings">
        <label class="ks-setting">${icon('sun-moon')}<span>明暗模式</span><select data-theme aria-label="明暗模式">${themeModes.map(([value,label])=>`<option value="${value}" ${draft.mode===value?'selected':''}>${label}</option>`).join('')}</select></label>
        <label class="ks-setting">${icon('palette')}<span>配色来源</span><select data-color-source aria-label="配色来源">${[['PRESET','预设调色板'],['CUSTOM','自选主色'],['SYSTEM','跟随系统壁纸']].map(([v,n])=>`<option value="${v}" ${draft.colorSource===v?'selected':''}>${n}</option>`).join('')}</select></label>
        ${draft.colorSource==='PRESET'?`<label class="ks-setting">${icon('swatch-book')}<span>调色板</span><select data-palette aria-label="调色板">${[['#c47a4a','杏橙'],['#5f7f64','茶绿'],['#587c9b','雾蓝'],['#8a729f','薰衣草'],['#a76f82','豆沙粉']].map(([v,n])=>`<option value="${v}" ${draft.presetColor===v?'selected':''}>${n}</option>`).join('')}</select></label>`:''}
        ${draft.colorSource==='CUSTOM'?`<label class="ks-custom-color-row"><input type="color" data-custom-picker aria-label="选择主色" value="${normalizeHex(draft.customColor)||style.customColor}"><input type="text" data-custom-hex aria-label="主色 HEX" value="${escapeText(draft.customColor)}" placeholder="#C47A4A" maxlength="7" spellcheck="false"></label>`:''}
        ${draft.colorSource==='SYSTEM'?`<div class="ks-setting">${icon('sparkles')}<span>使用系统壁纸配色</span><small>Android 12+</small></div>`:''}
      </div>
      <div data-appearance-editor hidden>
        ${draft.colorSource==='SYSTEM'?'<p class="ks-appearance-note">此处使用示意色板，真实配色由手机系统提供。</p>':''}
        <div class="ks-preview-modes" role="group" aria-label="预览明暗"><span class="ks-note">预览明暗</span><div class="ks-choices">${themeModes.slice(1).map(([value,label])=>choice(label,value,null,'preview-mode')).join('')}</div></div>
        <div class="ks-appearance-preview" data-appearance-preview aria-label="配色预览"><div class="ks-record">${recordIcon('sunrise')}<span class="ks-record-copy"><span class="ks-record-title">早餐 <span class="ks-pill">下厨</span></span><span class="ks-record-meta">燕麦粥、煎蛋</span></span></div><div class="ks-theme-swatches" aria-hidden="true"><span></span><span></span><span></span><span></span></div><div class="ks-record">${recordIcon('receipt-text')}<span class="ks-record-copy"><span class="ks-record-title">餐饮</span><span class="ks-record-meta">支出</span></span><span class="ks-currency-amount"><strong>−¥28.00</strong><small>CNY</small></span></div><span class="ks-primary ks-full" aria-hidden="true">保存</span></div>
        <p class="ks-error" data-appearance-error role="alert"></p><div class="ks-appearance-actions"><button type="button" class="ks-choice cursor-interaction" data-action="cancel-appearance">取消</button><button type="button" class="ks-primary cursor-interaction" data-action="apply-appearance">应用外观</button></div>
      </div>
      ${section('分类管理')}<div class="ks-settings"><div class="ks-setting">${icon('utensils')}<span>用餐方式</span><small>下厨、外卖</small></div><div class="ks-setting">${icon('cooking-pot')}<span>制作分类</span><small>炒菜、预制菜</small></div><button type="button" class="ks-setting ks-setting-link cursor-interaction" data-action="manage-categories" data-category-wallet="GLOBAL">${icon('tags')}<span>通用收支分类</span><small>默认钱包使用</small>${icon('chevron-right')}</button><button type="button" class="ks-setting ks-setting-link cursor-interaction" data-action="manage-categories" data-category-wallet="daily">${icon('wallet')}<span>钱包专属分类</span><small>按钱包设置收入／支出</small>${icon('chevron-right')}</button></div>${section('数据与备份')}<div class="ks-settings"><div class="ks-setting">${icon('hard-drive')}<span>文件夹自动备份</span><small>未启用</small></div><div class="ks-setting">${icon('cloud')}<span>GitHub 私有仓库</span><small>未配置</small></div><div class="ks-setting">${icon('file-json')}<span>JSON 备份与恢复</span></div><div class="ks-setting">${icon('sheet')}<span>CSV 导入与导出</span></div></div><p class="ks-footnote">KitsuneApp · 本地优先，安心记录</p>`;
    }
    function pageCategoryManager(){
      const wallet=state.categoryWalletId==='GLOBAL'?null:wallets.find(w=>w.id===state.categoryWalletId);
      const scope=state.financialScope,mode=scopeMode(wallet,scope);
      const own=wallet?walletFinancialCategories[wallet.id][scope]:sharedFinancialCategories[scope];
      const editable=!wallet||mode==='CUSTOM';
      const shown=editable?own:sharedFinancialCategories[scope];
      const current=own.find(c=>c.id===state.editingFinancialCategoryId);
      const name=wallet?wallet.name:'通用收支分类';
      return `<div class="ks-category-manager">
        <p class="ks-note">钱包没有设置专属分类时，自动使用通用分类；收入与支出分别设置。</p>
        <label><span class="ks-label">管理对象</span><select class="ks-control" data-category-wallet>
          <option value="GLOBAL" ${!wallet?'selected':''}>通用分类（所有默认钱包）</option>
          ${wallets.map(w=>`<option value="${w.id}" ${wallet?.id===w.id?'selected':''}>${escapeText(w.name)} · ${w.code}</option>`).join('')}
        </select></label>
        <fieldset><legend class="ks-label">收支类型</legend><div class="ks-choices">
          ${[['EXPENSE','支出'],['INCOME','收入']].map(([code,label])=>`<button type="button" class="ks-choice cursor-interaction" data-financial-scope="${code}" aria-pressed="${scope===code}">${label}</button>`).join('')}
        </div></fieldset>
        ${wallet?`<label><span class="ks-label">${escapeText(name)} · ${scope==='EXPENSE'?'支出':'收入'}分类来源</span>
          <select class="ks-control" data-category-mode><option value="DEFAULT" ${mode==='DEFAULT'?'selected':''}>使用通用分类</option><option value="CUSTOM" ${mode==='CUSTOM'?'selected':''}>使用本钱包专属分类</option></select>
        </label>`:''}
        <p class="ks-note">${wallet?(mode==='DEFAULT'?'使用通用分类。切为专属后，不会与通用列表混合。':'使用本钱包专属分类；即使没有自定义项，也只保留“未分类”。'):'通用类别供未开启专属分类的钱包使用。'}</p>
        ${section((wallet?escapeText(name)+' · ':'')+(scope==='EXPENSE'?'支出':'收入')+'分类')}
        <div class="ks-settings ks-category-rows">
          ${shown.map(c=>`<div class="ks-setting"><span>${escapeText(c.name)}${c.archived?'<small class="ks-note"> · 已停用</small>':''}</span>
          ${editable?`<button type="button" class="ks-choice cursor-interaction" data-edit-financial-category="${c.id}">改名</button><button type="button" class="ks-choice cursor-interaction" data-archive-financial-category="${c.id}">${c.archived?'启用':'停用'}</button>`:'<small>通用</small>'}</div>`).join('')}
          <div class="ks-setting"><span>未分类 <small class="ks-note">· 固定兜底</small></span><small>所有钱包可用</small></div>
        </div>
        ${editable?`<form class="ks-form ks-category-form" data-financial-category-form>
          <label><span class="ks-label">${current?'修改分类名称':'添加'+(wallet?'本钱包专属':'通用')+'分类'}</span><input name="categoryName" class="ks-control" value="${escapeText(current?.name||'')}" placeholder="输入分类名称" maxlength="30" required></label>
          <p class="ks-error" role="alert"></p>
          <div class="ks-form-actions">${current?'<button type="button" class="ks-choice" data-action="cancel-category-edit">取消修改</button>':'<span class="ks-note">不会更改旧账目</span>'}<button type="submit" class="ks-primary">${current?'保存修改':'新增分类'}</button></div>
        </form>`:`<button type="button" class="ks-choice ks-full cursor-interaction" data-action="edit-general-categories">编辑通用分类</button>`}
        <p class="ks-footnote">交互预览 · 可新增、改名、停用；正式排序及数据备份由 Android 实现。</p>
      </div>`;
    }

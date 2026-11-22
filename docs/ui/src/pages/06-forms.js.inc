    function pageMealForm(){return `<form class="ks-form" data-meal-form>
      <div class="ks-field-row"><label><span class="ks-label">日期</span><input type="date" name="date" class="ks-control" value="${state.date}" required></label><label><span class="ks-label">餐次</span><select name="slot" class="ks-control">${slots.map((s,i)=>`<option value="${i}" ${i===state.slot?'selected':''}>${s}</option>`).join('')}</select></label></div>
      <fieldset><legend class="ks-label">是否计划在家吃<span class="ks-required">必选</span></legend><div class="ks-choices">${choice('是',true,state.atHome,'home-choice')}${choice('否',false,state.atHome,'home-choice')}</div></fieldset>
      <fieldset><legend class="ks-label">用餐方式<span class="ks-required">${state.atHome===true?'必选':'单选 · 可不填'}</span></legend><div class="ks-choices">${['下厨','外卖'].map(m=>choice(m,m,state.method,'method')).join('')}</div></fieldset>
      ${state.method==='下厨'?`<fieldset><legend class="ks-label">制作分类<span class="ks-required">多选 · 选填</span></legend><div class="ks-choices">${['炒菜','预制菜',...state.categories.filter(c=>!['炒菜','预制菜'].includes(c))].map(c=>choice(escapeText(c),c,state.categories.includes(c)?c:null,'category')).join('')}<button type="button" class="ks-choice cursor-interaction" data-action="new-category">＋ 新建</button></div><div data-new-category hidden><label class="ks-label" style="margin-top:10px">新分类名称<input class="ks-control" data-category-name placeholder="例如：蒸煮" maxlength="20"></label><button type="button" class="ks-text-btn cursor-interaction" data-action="confirm-category">添加到草稿</button></div></fieldset>`:''}
      <label><span class="ks-label">菜品内容</span><textarea name="dishes" class="ks-control" rows="2" placeholder="今天吃了什么？">${escapeText(state.dishes||'')}</textarea></label>
      <label><span class="ks-label">备注</span><textarea name="note" class="ks-control" rows="2" placeholder="还有什么想记下的？">${escapeText(state.note||'')}</textarea></label>
      ${formFooter(saveButton(`保存${slots[state.slot]}记录`)+(state.editing?deleteButton('meal','删除这条用餐记录'):''),'交互预览 · 仅保存到当前演示')}
    </form>`;}
    function pageEntry(){return `<form data-entry-form class="ks-form">
      <fieldset><legend class="ks-label">收支类型</legend><div class="ks-choices">${['支出','收入'].map(t=>choice(t,t,state.entryType,'entry-type')).join('')}</div></fieldset>
      <label><span class="ks-label">日期时间</span><input type="datetime-local" name="dateTime" value="${escapeText(state.entryDraft.dateTime)}" class="ks-control" required></label>
      <div class="ks-field-row"><label><span class="ks-label">钱包</span><select name="wallet" class="ks-control" required><option value="" ${!entryWallet()?'selected':''} disabled>请选择钱包</option>${wallets.map(w=>`<option value="${w.id}" ${entryWallet()?.id===w.id?'selected':''}>${w.name} · ${w.code}${w.separateAccounting?'（独立核算）':''}</option>`).join('')}</select></label><label><span class="ks-label">分类</span><select name="category" class="ks-control" required><option value="" ${!state.entryDraft.category?'selected':''} disabled>请选择分类</option>${effectiveFinancialCategories(state.entryDraft.walletId,state.entryType).map(c=>`<option value="${c.id}" ${state.entryDraft.category===c.id?'selected':''}>${escapeText(c.name)}</option>`).join('')}</select></label></div>
      <p class="ks-note">分类来源：${entryWallet()?scopeMode(entryWallet(),financialScopeOf(state.entryType))==='CUSTOM'?'本钱包专属分类':'通用分类':'请先选择钱包'}。切换钱包后如无对应分类，请重新选择。</p>
      <label><span class="ks-label">备注</span><input name="note" class="ks-control" placeholder="这笔钱用在了哪里？" value="${escapeText(state.entryDraft.note)}"></label>
      <div><label class="ks-label" for="ks-amount">金额 · ${entryWallet()?.code||'请选择钱包'}</label><div class="ks-amount-row"><input id="ks-amount" class="ks-amount-input" name="amount" inputmode="decimal" placeholder="${(entryWallet()?.scale??2)?'0.00':'0'}" autocomplete="off" value="${escapeText(state.amount)}" aria-label="金额"><button type="button" class="ks-calc-trigger cursor-interaction" data-action="open-calculator">${icon('calculator')}计算器</button></div></div>
      ${formFooter(saveButton(`保存${state.entryType}`),'交互预览 · 仅保存到当前演示')}
    </form>`;}
    function calculatorSheet(){return `<div class="ks-modal-overlay"><section class="ks-calc-sheet" role="dialog" aria-modal="true" aria-label="金额计算器"><div class="ks-calc-body"><div class="ks-row"><h3>金额计算器</h3><button type="button" class="ks-text-btn cursor-interaction" data-calc-action="cancel">关闭</button></div><label><span class="ks-note">${entryWallet()?.code||'未选择钱包'} · 支持加减乘除</span><input class="ks-amount-input" data-calc-expression inputmode="text" aria-label="计算表达式" maxlength="80" placeholder="${(entryWallet()?.scale??2)?'0.00':'0'}" value="${escapeText(state.calcExpression)}"></label><div class="ks-calculator" role="group" aria-label="计算键盘">${['C','⌫','÷','×','7','8','9','−','4','5','6','+','1','2','3','=','0','.'].map(k=>`<button type="button" class="ks-key ${['÷','×','−','+','='].includes(k)?'ks-operator':''} ${k==='0'?'ks-zero':''} ${k==='='?'ks-equal':''} cursor-interaction" data-key="${k}" aria-label="${k==='⌫'?'退格':k==='C'?'清空':k}">${k}</button>`).join('')}</div></div><div class="ks-calc-footer"><p class="ks-calc-error" role="alert"></p><div class="ks-calc-actions"><button type="button" class="ks-choice cursor-interaction" data-calc-action="cancel">取消</button><button type="button" class="ks-primary cursor-interaction" data-calc-action="apply">使用此金额</button></div></div></section></div>`;}
    function walletRangeDialog(){
      const mode=state.walletDraftMode,manual=mode==='MANUAL',canApply=!manual||state.walletDraftIds.length>0;
      const modeLabels={REGULAR:'常规钱包',ALL:'全部钱包',MANUAL:'手动选择'};
      const hints={REGULAR:'仅常规钱包（独立核算关闭），包括归档历史。',ALL:'包括独立核算钱包和归档历史。',MANUAL:'独立核算钱包在下方列表中默认不勾选。'};
      const options=wallets.map(w=>`<label class="ks-wallet-check cursor-interaction"><input type="checkbox" data-wallet-option="${w.id}" ${state.walletDraftIds.includes(w.id)?'checked':''}><span>${escapeText(w.name)} · ${w.code}</span>${w.separateAccounting?'<small>独立核算</small>':''}</label>`).join('');
      return `<div class="ks-modal-overlay ks-wallet-overlay"><section class="ks-wallet-dialog" role="dialog" aria-modal="true" aria-label="钱包范围">
        <h3>钱包范围</h3><div class="ks-wallet-body">
          <p class="ks-note">默认只统计常规钱包；独立核算钱包可手动加入。</p>
          <div class="ks-choices" role="group" aria-label="钱包范围模式">${Object.entries(modeLabels).map(([value,label])=>choice(label,value,mode,'wallet-mode')).join('')}</div>
          <p class="ks-note">${hints[mode]}</p>${manual?options:''}
          ${canApply?'':'<p class="ks-note">请选择至少一个钱包，或恢复默认。</p>'}
        </div><div class="ks-wallet-actions">
          <button type="button" class="ks-text-btn" data-wallet-action="default">恢复默认</button>
          <button type="button" class="ks-text-btn" data-wallet-action="cancel">取消</button>
          <button type="button" class="ks-text-btn" data-wallet-action="apply" ${canApply?'':'disabled'}>应用</button>
        </div></section></div>`;
    }
        function pageMealFilter(){
      const f=state.mealFilterDraft,methods=[...new Set(['下厨','外卖',...allMeals().map(m=>m.method).filter(Boolean)])],preparations=[...new Set(['炒菜','预制菜',...allMeals().flatMap(m=>m.categories)])];
      const select=(label,name,choices)=>`<label><span class="ks-label">${label}</span><select class="ks-control" name="${name}">${choices.map(([value,label])=>`<option value="${escapeText(value)}" ${f[name]===value?'selected':''}>${escapeText(label)}</option>`).join('')}</select></label>`;
      return `<form class="ks-form" data-meal-filter-form>
        <fieldset><legend class="ks-label">日期范围</legend><div class="ks-field-row"><label><span class="ks-note">开始日期</span><input class="ks-control" type="date" name="from" value="${f.from}" aria-label="开始日期"></label><label><span class="ks-note">结束日期</span><input class="ks-control" type="date" name="to" value="${f.to}" aria-label="结束日期"></label></div><span class="ks-note">留空可查全部时间</span></fieldset>
        <div class="ks-field-row">${select('餐次','slot',[['any','全部餐次'],...slots.map((s,i)=>[String(i),s])])}${select('计划在家吃','atHome',[['any','不限'],['yes','是'],['no','否']])}</div>
        ${select('用餐方式','method',[['any','全部方式'],...methods.map(m=>[m,m]),['none','未填写方式']])}
        <fieldset><legend class="ks-label">制作分类<span class="ks-required">多选，匹配任一项</span></legend><div class="ks-preparation-choices">${preparations.map(c=>`<label class="ks-preparation-check cursor-interaction"><input type="checkbox" name="preparation" value="${escapeText(c)}" ${f.preparations.includes(c)?'checked':''}>${escapeText(c)}</label>`).join('')}</div></fieldset>
        <label><span class="ks-label">菜品／备注关键词</span><input class="ks-control" type="search" name="keyword" value="${escapeText(f.keyword)}" placeholder="例如：番茄、和朋友" maxlength="100"></label>
        <p class="ks-note" style="margin:0">不同条件同时满足；未选择的条件不限制。</p>
        ${formFooter(`<div class="ks-form-actions"><button type="button" class="ks-choice cursor-interaction" data-action="reset-meal-filter">重置条件</button>${saveButton('应用筛选')}</div>`)}
      </form>`;
    }

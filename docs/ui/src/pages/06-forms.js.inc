    const walletSelectionDetails=w=>[w.code,w.separateAccounting?'独立核算':'',w.archived?'已归档':''].filter(Boolean).join(' · ');
    const walletSelectionOption=w=>[w.id,w.name,walletSelectionDetails(w)];
    const knownCurrencies=['AUD','CAD','CHF','CNY','EUR','GBP','HKD','JPY','KRW','MOP','SGD','TWD','USD'];
    const displayCurrency=new Intl.DisplayNames(['zh-CN'],{type:'currency'});
    const mealMethodLabel=m=>m+(archivedMealMethods.includes(m)?'（已停用）':'');
    const mealPreparationLabel=c=>c+(archivedMealPreparations.includes(c)?'（已停用）':'');
    const appearanceSources=[['PRESET','预设调色板'],['CUSTOM','自选主色'],['SYSTEM','跟随系统壁纸']];
    const appearancePalettes=[['#c47a4a','杏橙'],['#5f7f64','茶绿'],['#587c9b','雾蓝'],['#8a729f','薰衣草'],['#a76f82','豆沙粉']];
    function selectionControl(kind,label,value,glyph='',disabled=false){
      return `<div class="ks-selection-field"><span class="ks-label" id="ks-choice-${kind}">${escapeText(label)}</span>
        <button type="button" class="ks-control ks-selection-trigger" data-select-open="${kind}" aria-labelledby="ks-choice-${kind} ks-choice-${kind}-value" aria-haspopup="dialog" ${disabled?'disabled':''}>
          ${glyph}<span id="ks-choice-${kind}-value">${escapeText(value||'请选择')}</span>${icon('chevron-down')}
        </button></div>`;
    }
    function selectionOption([value,label,detail,glyph],selected,action){
      return `<button type="button" class="ks-selection-option ks-selection-single" ${action}="${escapeText(value)}" aria-pressed="${selected}" ${detail?'data-selection-detail':''}>
        ${glyph||''}<span class="ks-selection-copy">${escapeText(label)}${detail?`<small>${escapeText(detail)}</small>`:''}</span>
        <span class="ks-selection-indicator" aria-hidden="true">${selected?icon('check'):''}</span></button>`;
    }
    function selectionCheck(label,attributes,checked,detail='',glyph=''){
      return `<label class="ks-selection-option" ${detail?'data-selection-detail':''}>
        ${glyph}<span class="ks-selection-copy">${escapeText(label)}${detail?`<small>${escapeText(detail)}</small>`:''}</span>
        <input type="checkbox" ${attributes} ${checked?'checked':''}></label>`;
    }
    function selectionPanel(title,body,footer='',{attrs='',form=false,header=''}={}){
      const tag=form?'form':'section';
      return `<div class="ks-modal-overlay ks-selection-overlay"><${tag} class="ks-selection-panel" role="dialog" aria-modal="true" aria-label="${escapeText(title)}" ${attrs}>
        <div class="ks-selection-handle" aria-hidden="true"><span></span></div>
        <div class="ks-selection-heading"><h3>${escapeText(title)}</h3><button type="button" class="ks-selection-close" data-select-close aria-label="关闭">${icon('x')}</button></div>
        ${header}<div class="ks-selection-body">${body}</div>${footer?`<div class="ks-selection-footer">${footer}</div>`:''}
      </${tag}></div>`;
    }
    function currencyOptions(query){
      const search=query.trim().toLowerCase();
      const choices=knownCurrencies.map(code=>[code,code,displayCurrency.of(code)])
        .filter(([,code,name])=>code.toLowerCase().includes(search)||name.toLowerCase().includes(search));
      return choices.map(option=>selectionOption(option,option[0]===state.walletEditDraft.code,'data-select-value')).join('')
        || '<p class="ks-note">没有匹配币种</p>';
    }
    function singleSelectionPanel(){
      const kind=state.singleSelection,wallet=entryWallet(),scope=financialScopeOf(state.entryType);
      const managedWallet=state.categoryWalletId==='GLOBAL'?null:wallets.find(w=>w.id===state.categoryWalletId);
      let title,choices,current,subtitle='',footer='';
      switch(kind){
        case 'entry-wallet':
          title='选择钱包';choices=wallets.filter(w=>!w.archived).map(walletSelectionOption);current=wallet?.id;break;
        case 'entry-category':
          title='选择分类';current=state.entryDraft.category;
          subtitle=(scopeMode(wallet,scope)==='CUSTOM'?'本钱包专属分类':'通用分类')+' · '+state.entryType;
          choices=effectiveFinancialCategories(wallet?.id,state.entryType).map(c=>[c.id,c.name,'',categoryGlyph(c.icon)]);
          footer=`<button type="button" class="ks-text-btn ks-selection-add" data-action="add-entry-category">${icon('plus')}新增分类</button>`;break;
        case 'meal-slot':
          title='选择餐次';choices=slots.map((s,i)=>[String(i),s]);current=String(state.slot);break;
        case 'category-wallet':
          title='选择管理对象';choices=[['GLOBAL','通用分类'],...wallets.map(walletSelectionOption)];current=state.categoryWalletId;break;
        case 'category-mode':
          title=`选择${managedWallet?managedWallet.name+' · ':''}${state.financialScope==='EXPENSE'?'支出':'收入'}分类来源`;
          choices=[['DEFAULT','使用通用分类'],['CUSTOM','使用本钱包专属分类']];
          current=scopeMode(managedWallet,state.financialScope);break;
        case 'appearance-mode':
          title='选择明暗模式';choices=themeModes;current=state.appearanceDraft.mode;break;
        case 'appearance-source':
          title='选择配色来源';choices=appearanceSources;current=state.appearanceDraft.colorSource;break;
        case 'appearance-palette':
          title='选择调色板';choices=appearancePalettes;current=state.appearanceDraft.presetColor;break;
        case 'csv-method':
          title='选择映射 外送';choices=mealMethods.map(m=>[m,m]);current=state.csvMethod;break;
        case 'csv-wallet':
          title='选择映射 随身账户 · CNY';choices=wallets.filter(w=>w.code==='CNY').map(walletSelectionOption);current=state.csvWallet;break;
        case 'currency':
          return selectionPanel('选择币种',currencyOptions(''),'',{header:
            '<label><span class="ks-label">搜索代码或名称</span><input class="ks-control" type="search" data-currency-search></label>'});
      }
      const options=choices.map(option=>selectionOption(option,option[0]===current,'data-select-value')).join('');
      return selectionPanel(title,(subtitle?`<p class="ks-note">${escapeText(subtitle)}</p>`:'')+
        (options||'<p class="ks-note">没有可选项目</p>'),footer);
    }
    function mealPrepTargetOptions(target){
      return target==='form'?mealFormOptions().preparations:allMealFilterPreparations();
    }
    function mealPrepOptionsMarkup(all,selected,query){
      const search=query.trim().toLocaleLowerCase();
      const visible=search?all.filter(c=>mealPreparationLabel(c).toLocaleLowerCase().includes(search)):all;
      return visible.map(c=>selectionCheck(mealPreparationLabel(c),`data-meal-prep-option="${escapeText(c)}"`,selected.includes(c))).join('')
        || '<p class="ks-note">没有匹配分类</p>';
    }
    function mealPreparationPickerDialog(){
      const draft=state.mealPrepPickerDraft,all=mealPrepTargetOptions(draft.target);
      const count=all.filter(c=>draft.selected.includes(c)).length;
      const header=`<label><span class="ks-label">搜索分类</span><input class="ks-control" type="search" data-meal-prep-search value="${escapeText(draft.query||'')}"></label>
        <div class="ks-selection-group-heading">
          <span class="ks-note" data-meal-prep-count>已选 ${count}/${all.length}</span>
          <div class="ks-selection-group-actions">
            <button type="button" class="ks-text-btn" data-meal-prep-action="all">全选</button>
            <button type="button" class="ks-text-btn" data-meal-prep-action="none">全不选</button>
          </div>
        </div>`;
      const body=mealPrepOptionsMarkup(all,draft.selected,draft.query||'');
      const footer='<button type="button" class="ks-primary ks-full" data-meal-prep-action="apply">应用</button>';
      return selectionPanel('选择制作分类',body,footer,{header});
    }
    const addMealOption = kind => `<button type="button" class="ks-icon-btn cursor-interaction" data-add-meal-option="${kind}" aria-label="新增${mealOptionLabels[kind]}">${icon('plus')}</button>`;
    // Editing keeps the original record's archived method and categories selectable; new records only list active items.
    const mealFormOptions=()=>{
      const original=state.editing?state.records[state.editingDate]?.[state.editingSlot]:null;
      const methods=[...mealMethods,...archivedMealMethods.filter(m=>m===original?.method||m===state.method)];
      const preparations=[...mealPreparations,...archivedMealPreparations.filter(c=>original?.categories?.includes(c)||state.categories.includes(c))];
      return {methods,preparations};
    };
    function pageMealForm(){const options=mealFormOptions(),extraPreps=options.preparations.slice(8).filter(c=>state.categories.includes(c));return `<form class="ks-form" data-meal-form>
      <div class="ks-field-row"><label><span class="ks-label">日期</span><input type="date" name="date" class="ks-control" value="${state.date}" required></label>${selectionControl('meal-slot','餐次',slots[state.slot])}</div>
      <fieldset><legend class="ks-label">是否计划在家吃<span class="ks-required">必选</span></legend><div class="ks-choices">${choice('是',true,state.atHome,'home-choice')}${choice('否',false,state.atHome,'home-choice')}</div></fieldset>
      <fieldset><legend class="ks-label">用餐方式<span class="ks-required">${state.atHome?'必选':'单选 · 可不填'}</span></legend><div class="ks-choices">${options.methods.map(m=>choice(escapeText(mealMethodLabel(m)),m,state.method,'method')).join('')}${addMealOption('method')}</div></fieldset>
      ${state.method==='下厨'?`<fieldset><legend class="ks-label">制作分类<span class="ks-required">多选 · 选填</span></legend><div class="ks-choices">${options.preparations.slice(0,8).map(c=>choice(escapeText(mealPreparationLabel(c)),c,state.categories.includes(c)?c:null,'category')).join('')}${options.preparations.length>8?`<button type="button" class="ks-choice cursor-interaction" data-open-meal-preps="form">${icon('list-filter')}更多分类</button>`:''}${addMealOption('preparation')}</div>${extraPreps.length?`<p class="ks-note">已选更多分类 · ${escapeText(extraPreps.join('、'))}</p>`:''}</fieldset>`:''}
      <label><span class="ks-label">菜品内容</span><input type="text" name="dishes" class="ks-control" placeholder="今天吃了什么？" value="${escapeText(state.dishes||'')}"></label>
      <label><span class="ks-label">备注</span><input type="text" name="note" class="ks-control" placeholder="还有什么想记下的？" value="${escapeText(state.note||'')}"></label>
      ${formFooter(saveButton(`保存${slots[state.slot]}记录`)+(state.editing?deleteButton('meal','删除这条用餐记录'):''),'交互预览 · 仅保存到当前演示')}
    </form>`;}
    function mealOptionDialog(){
      const title='新增'+mealOptionLabels[state.addingMealOption];
      return `<div class="ks-modal-overlay" data-meal-option-overlay>
        <form class="ks-period-dialog ks-form" data-meal-option-form role="dialog" aria-modal="true" aria-label="${title}">
          <h3>${title}</h3>
          <label><span class="ks-label">名称</span><input class="ks-control" name="optionName" required></label>
          <p class="ks-error" data-meal-option-error role="alert"></p>
          <div class="ks-period-actions">
            <button type="button" class="ks-text-btn" data-action="cancel-meal-option">取消</button>
            <button type="submit" class="ks-primary" disabled>确定</button>
          </div>
        </form></div>`;
    }
    function categoryIconPicker(selected=''){
      return `<fieldset><legend class="ks-label">分类图标</legend><div class="ks-choices ks-category-icons">
        ${Object.entries(categoryIcons).map(([key,[label]])=>`<label class="ks-choice">
          <input type="radio" name="categoryIcon" value="${key}" ${selected===key?'checked':''}>
          ${categoryGlyph(key)}<span>${label}</span></label>`).join('')}
      </div></fieldset>`;
    }
    function entryCategoryDialog(){
      return `<div class="ks-modal-overlay ks-period-overlay" data-entry-category-overlay>
        <form class="ks-period-dialog ks-form" data-entry-category role="dialog" aria-modal="true" aria-label="新增分类">
          <h3>新增分类</h3>
          <div class="ks-period-body">
            <label><span class="ks-label">分类名称</span><input name="categoryName" class="ks-control" placeholder="输入分类名称" maxlength="30" required></label>
            ${categoryIconPicker()}
            <p class="ks-error" role="alert"></p>
          </div>
          <div class="ks-period-actions">
            <button type="button" class="ks-text-btn" data-action="cancel-entry-category">取消</button>
            <button type="submit" class="ks-text-btn">保存</button>
          </div>
        </form></div>`;
    }
    function pageEntry(){return `<form data-entry-form class="ks-form">
      <fieldset><legend class="ks-label">收支类型</legend><div class="ks-choices">${['支出','收入'].map(t=>choice(t,t,state.entryType,'entry-type')).join('')}</div></fieldset>
      <div><span class="ks-label" id="ks-amount-label">金额 · ${entryWallet()?.code||'请选择钱包'}</span>
        <button type="button" class="ks-amount-input ks-amount-trigger" data-action="open-calculator" aria-labelledby="ks-amount-label ks-amount-value" ${entryWallet()?'':'disabled'}>
          <span id="ks-amount-value">${escapeText(state.amount|| (entryWallet()?.scale===0?'0':'0.00'))}</span></button>
      </div>
      <fieldset><legend class="ks-label">日期时间</legend><div class="ks-field-row ks-entry-date-time">
        <input type="date" name="date" value="${escapeText(state.entryDraft.dateTime.slice(0,10))}" class="ks-control" aria-label="日期" required>
        <button type="button" class="ks-control" data-action="open-entry-time" aria-haspopup="dialog" aria-label="时间 ${state.entryDraft.dateTime.slice(-5)}">${state.entryDraft.dateTime.slice(-5)}</button>
      </div></fieldset>
      <div class="ks-field-row">
        ${selectionControl('entry-wallet','钱包',entryWallet()?`${entryWallet().name} · ${entryWallet().code}`:'未选择')}
        ${selectionControl('entry-category','分类',categoryById(state.entryDraft.category)?.name,
          state.entryDraft.category?categoryGlyph(categoryById(state.entryDraft.category)?.icon):'',!entryWallet())}
      </div>
      ${entryWallet()&&!state.entryDraft.category?'<p class="ks-note">请重新选择该钱包的分类</p>':''}
      <label><span class="ks-label">备注</span><input name="note" class="ks-control" placeholder="这笔钱用在了哪里？" value="${escapeText(state.entryDraft.note)}"></label>
      ${formFooter(saveButton(`保存${state.entryType}`),'交互预览 · 仅保存到当前演示')}
    </form>`;}
    const validTimePart=(value,part)=>/^\d{2}$/.test(value)&&Number(value)<=(part==='hour'?23:59);
    const timeDigits=value=>String(value).padStart(2,'0');
    function entryTimeDialog(){
      const d=state.entryTimeDraft;
      const field=(part,label)=>d.clock
        ?`<button type="button" class="ks-time-digits" data-time-part="${part}" aria-pressed="${d.part===part}" aria-label="${label} ${d[part]}">${d[part]}</button>`
        :`<label><input class="ks-time-digits" name="${part}" type="text" inputmode="numeric" enterkeyhint="${part==='hour'?'next':'done'}" maxlength="2" autocomplete="off" value="${d[part]}" aria-invalid="${d[part].length===2&&!validTimePart(d[part],part)}"><span class="ks-note">${label}</span></label>`;
      const isHour=d.part==='hour',selected=Number(d[d.part]),step=isHour?1:5;
      const radius=value=>isHour&&value>=12?26:40;
      const angle=value=>value/step*30;
      const options=Array.from({length:isHour?24:60/step},(_,i)=>i*step);
      if(!isHour&&!options.includes(selected))options.push(selected);
      const dial=d.clock?`<div class="ks-time-face" role="group" aria-label="${isHour?'小时':'分钟'}">
        <span class="ks-time-hand" aria-hidden="true" style="height:${radius(selected)}%;transform:rotate(${angle(selected)}deg)"></span>
        ${options.map(value=>{
          const a=angle(value)*Math.PI/180,r=radius(value);
          return `<button type="button" data-time-value="${value}" aria-pressed="${value===selected}" style="left:${50+r*Math.sin(a)}%;top:${50-r*Math.cos(a)}%">${isHour?value:timeDigits(value)}</button>`;
        }).join('')}
      </div>`:'';
      return `<div class="ks-modal-overlay ks-time-overlay"><form class="ks-period-dialog ks-time-dialog ${d.clock?'ks-time-clock':''}" data-entry-time role="dialog" aria-modal="true" aria-label="选择时间">
        <h3>选择时间</h3>
        <div class="ks-time-fields">${field('hour','小时')}<span class="ks-time-colon" aria-hidden="true">:</span>${field('minute','分钟')}</div>
        ${dial}
        <div class="ks-period-actions">
          <button type="button" class="ks-icon-btn" data-time-action="mode" aria-label="${d.clock?'输入时间':'使用时钟'}">${icon(d.clock?'keyboard':'clock')}</button>
          <button type="button" class="ks-text-btn" data-time-action="cancel">取消</button>
          <button type="submit" class="ks-text-btn" ${validTimePart(d.hour,'hour')&&validTimePart(d.minute,'minute')?'':'disabled'}>确定</button>
        </div>
      </form></div>`;
    }
    function calculatorSheet(){
      return `<div class="ks-modal-overlay ks-calculator-overlay">
        <section class="ks-selection-panel ks-calc-sheet" role="dialog" aria-modal="true" aria-labelledby="ks-calculator-title">
          <div class="ks-selection-handle" aria-hidden="true"><span></span></div>
          <header class="ks-selection-heading"><h3 id="ks-calculator-title">金额计算器</h3>
            <button type="button" class="ks-selection-close cursor-interaction" data-calc-action="cancel" aria-label="关闭">${icon('x')}</button>
          </header>
          <div class="ks-calc-body">
            <div class="ks-calc-meta"><span class="ks-note">${entryWallet().code}</span>
              <div class="ks-calc-readout" data-calc-expression aria-label="计算表达式"></div>
            </div>
            <div class="ks-calc-readout ks-calc-result" data-calc-result aria-label="计算结果" aria-live="polite"></div>
            <div class="ks-calculator" role="group" aria-label="计算键盘">
              ${['C','⌫','÷','×','7','8','9','−','4','5','6','+','1','2','3','=','0','.'].map(k=>
                `<button type="button" class="ks-key ${calcOperators.includes(k)||k==='='?'ks-operator':''} ${k==='C'||k==='⌫'?'ks-utility':''} ${k==='0'?'ks-zero':''} ${k==='='?'ks-equal':''} cursor-interaction"
                  data-key="${k}" aria-label="${k==='⌫'?'退格':k==='C'?'清空':k}">${k==='⌫'?icon('delete'):k}</button>`).join('')}
            </div>
          </div>
          <p class="ks-calc-error" role="alert"></p>
          <button type="button" class="ks-primary ks-full cursor-interaction" data-calc-action="apply">确认金额</button>
        </section>
      </div>`;
    }
    function mealCalendarDialog(){
      const d=state.mealCalendar,[year,month]=d.month.split('-').map(Number);
      const first=new Date(`${d.month}-01T00:00:00Z`),offset=(first.getUTCDay()+6)%7;
      const last=new Date(first);last.setUTCMonth(month,0);
      const days=Array.from({length:42},(_,index)=>{
        const day=index-offset+1;
        if(day<1||day>last.getUTCDate())return '<span aria-hidden="true"></span>';
        const date=`${d.month}-${String(day).padStart(2,'0')}`;
        return `<button type="button" class="ks-calendar-day cursor-interaction" data-calendar-day="${date}" aria-label="${date}" aria-pressed="${d.selected===date}" ${date===demoToday?'aria-current="date"':''}>${day}</button>`;
      }).join('');
      const yearOkay=validCalendarYear(d.year),text=d.mode==='text',jump=d.mode==='jump';
      const part=text?`<label><span class="ks-label">日期（YYYY-MM-DD）</span><input class="ks-control" data-calendar-text value="${escapeText(d.text)}" placeholder="例如 2026-11-17"></label><p class="ks-error" data-calendar-error role="alert">${d.text&&!isDate(d.text)?'请输入有效日期':''}</p>`:`
        <span class="ks-calendar-selected">${d.selected}</span>
        <div class="ks-calendar-month">
          <button type="button" class="ks-icon-btn" data-calendar-step="-1" aria-label="上个月" ${jump||d.month==='0001-01'?'disabled':''}>${icon('chevron-left')}</button>
          <button type="button" class="ks-choice" data-calendar-action="jump" aria-label="选择年月" aria-expanded="${jump}">${icon('chevron-down')}${year} 年 ${month} 月</button>
          <button type="button" class="ks-icon-btn" data-calendar-step="1" aria-label="下个月" ${jump||d.month==='9999-12'?'disabled':''}>${icon('chevron-right')}</button>
        </div>
        ${jump?`<label><span class="ks-label">年份</span><input class="ks-control" data-calendar-year inputmode="numeric" value="${escapeText(d.year)}"></label>
          <p class="ks-error" data-calendar-error role="alert">${yearOkay?'':'请输入 1 至 9999 的年份'}</p>
          <div class="ks-period-months" role="group" aria-label="月份">${Array.from({length:12},(_,i)=>`<button type="button" class="ks-choice" data-calendar-month="${i+1}" aria-pressed="${Number(d.year)===year&&i+1===month}" ${yearOkay?'':'disabled'}>${i+1} 月</button>`).join('')}</div>`:
          `<div class="ks-calendar-grid" aria-hidden="true">${[...'一二三四五六日'].map(day=>`<span class="ks-note">${day}</span>`).join('')}</div><div class="ks-calendar-grid" role="group" aria-label="日期">${days}</div>`}`;
      return `<div class="ks-modal-overlay ks-calendar-overlay"><section class="ks-period-dialog ks-calendar-dialog" role="dialog" aria-modal="true" aria-label="选择用餐日期">
        <h3>选择用餐日期</h3><div class="ks-period-body">
          ${d.compact?'':`<button type="button" class="ks-text-btn ks-calendar-mode" data-calendar-action="mode">${text?'使用日历':'输入日期'}</button>`}
          ${part}
        </div><div class="ks-period-actions"><button type="button" class="ks-text-btn" data-calendar-action="cancel">取消</button><button type="button" class="ks-text-btn" data-calendar-action="apply" ${jump||(text&&!isDate(d.text))?'disabled':''}>确定</button></div>
      </section></div>`;
    }
    function periodDialog(){
      const draft=state.periodDraft,validYear=/^\d{1,4}$/.test(draft.year)&&Number(draft.year)>0;
      const mealStats=draft.target==='meals',title=mealStats?'选择统计月份':'日期范围';
      const ordinal=Number(draft.year)*12+Number(draft.month)-1;
      const step=(direction,label,iconName,enabled)=>`<button type="button" class="ks-icon-btn" data-period-step="${direction}" aria-label="${label}" ${enabled?'':'disabled'}>${icon(iconName)}</button>`;

      return `<div class="ks-modal-overlay ks-period-overlay"><form class="ks-period-dialog" data-period-form role="dialog" aria-modal="true" aria-label="${title}">
        <h3>${title}</h3>
        <div class="ks-period-body">
          ${mealStats?'':`<div class="ks-period-modes" role="group" aria-label="日期范围模式">${choice('按月','month',draft.mode,'period-mode')}${choice('自定义日期','range',draft.mode,'period-mode')}</div>`}
          ${draft.mode==='month'?`
            <label><span class="ks-label">年份</span><input type="text" inputmode="numeric" name="year" class="ks-control" value="${escapeText(draft.year)}"></label>
            <div class="ks-row">
              ${step(-1,'上个月','chevron-left',validYear&&ordinal>12)}
              <span data-period-month-label>${validYear?`${Number(draft.year)}年${draft.month}月`:''}</span>
              ${step(1,'下个月','chevron-right',validYear&&ordinal<119999)}
            </div>
            <div class="ks-period-months" role="group" aria-label="月份">${Array.from({length:12},(_,i)=>choice(`${i+1} 月`,String(i+1),draft.month,'period-month')).join('')}</div>
          `:`
            <label><span class="ks-label">开始日期</span><input type="date" name="from" class="ks-control" value="${draft.from}" min="0001-01-01" max="9999-12-31"></label>
            <label><span class="ks-label">结束日期</span><input type="date" name="to" class="ks-control" value="${draft.to}" min="0001-01-01" max="9999-12-31"></label>
          `}
          <p class="ks-error" data-period-error role="alert">${periodError(draft)}</p>
        </div>
        <div class="ks-period-actions">
          <button type="button" class="ks-text-btn" data-action="cancel-period">取消</button>
          <button type="submit" class="ks-text-btn" ${canApplyPeriod(draft)?'':'disabled'}>应用</button>
        </div></form></div>`;
    }
    function walletRangeDialog(){
      const mode=state.walletDraftMode,manual=mode==='MANUAL';
      const selected=wallets.filter(w=>state.walletDraftIds.includes(w.id)).length;
      const canApply=!manual||selected>0;
      const modes=[['REGULAR','常规钱包'],['ALL','全部钱包'],['MANUAL','手动选择']];
      const hints={REGULAR:'排除独立核算钱包，保留归档历史。',ALL:'包含独立核算钱包及归档历史。'};
      const segments=modes.map(([id,label])=>`<button type="button" class="ks-selection-segment" data-wallet-mode="${id}" aria-pressed="${mode===id}">${label}</button>`).join('');
      const options=manual?wallets.map(w=>selectionCheck(w.name,`data-wallet-option="${w.id}"`,
        state.walletDraftIds.includes(w.id),walletSelectionDetails(w))).join(''):'';
      const body=`<div class="ks-selection-segments" role="group" aria-label="钱包范围模式">${segments}</div>
        ${manual?`<section class="ks-selection-section" aria-labelledby="ks-manual-wallets">
          <div class="ks-selection-group-heading"><h4 id="ks-manual-wallets">选择钱包</h4>
            <span class="ks-note">已选 ${selected}/${wallets.length}</span></div>
          ${canApply?'':'<p class="ks-note">至少选择一个钱包。</p>'}${options}
        </section>`:`<p class="ks-note">${hints[mode]}</p>`}`;
      const footer=`<button type="button" class="ks-text-btn" data-wallet-action="default">恢复默认</button>
        <button type="button" class="ks-primary" data-wallet-action="apply" ${canApply?'':'disabled'}>应用</button>`;
      return selectionPanel('钱包范围',body,footer);
    }
    function mealFiltersDialog(){
      const f=state.mealFilterDraft;
      const preparations=allMealFilterPreparations();
      const methods=allMealFilterMethods();
      const datePreset=mealDatePreset(f);
      const dateField=(name,label)=>`<div class="ks-meal-filter-date">
        <div class="ks-filter-field-heading">
          <label class="ks-note" for="meal-filter-${name}">${label}</label>
          <button type="button" class="ks-text-btn" data-clear-meal-date="${name}" aria-label="清空${label}" ${f[name]?'':'hidden'}>清空</button>
        </div>
        <input class="ks-control" type="date" id="meal-filter-${name}" name="${name}" value="${f[name]}" min="0001-01-01" max="9999-12-31">
      </div>`;
      const chip=(label,selected,attrs)=>`<button type="button" class="ks-choice ks-meal-chip cursor-interaction" ${attrs} aria-pressed="${selected}">${selected?icon('check'):''}<span>${escapeText(label)}</span></button>`;
      const body=`
        <div>
          <div class="ks-filter-field-heading">
            <label class="ks-label" for="meal-filter-keyword">菜品／备注关键词</label>
            <button type="button" class="ks-text-btn" data-clear-meal-keyword aria-label="清空菜品／备注关键词" ${f.keyword?'':'hidden'}>清空</button>
          </div>
          <input class="ks-control" type="search" id="meal-filter-keyword" name="keyword" value="${escapeText(f.keyword)}" placeholder="例如：番茄、和朋友" maxlength="100">
        </div>
        <div class="ks-selection-section" role="group" aria-labelledby="ks-meal-filter-dates-label"><span class="ks-label" id="ks-meal-filter-dates-label">日期范围</span>
          <div class="ks-choices">${chip('本月',datePreset==='month','data-meal-date-preset="month"')}${chip('所有时间',datePreset==='all','data-meal-date-preset="all"')}</div>
          <div class="ks-field-row">${dateField('from','开始日期')}${dateField('to','结束日期')}</div>
        </div>
        <div class="ks-selection-section" role="group" aria-labelledby="ks-meal-filter-slot-label"><span class="ks-label" id="ks-meal-filter-slot-label">餐次<span class="ks-required">多选，不选不限</span></span>
          <div class="ks-choices">${slots.map((s,i)=>chip(s,f.slots.includes(String(i)),`data-meal-filter-slot="${i}"`)).join('')}</div>
        </div>
        <div class="ks-selection-section" role="group" aria-labelledby="ks-meal-filter-home-label"><span class="ks-label" id="ks-meal-filter-home-label">是否计划在家吃<span class="ks-required">多选，不选不限</span></span>
          <div class="ks-choices">${[['yes','是'],['no','否']].map(([v,l])=>chip(l,f.atHome.includes(v),`data-meal-filter-home="${v}"`)).join('')}</div>
        </div>
        <div class="ks-selection-section" role="group" aria-labelledby="ks-meal-filter-method-label"><span class="ks-label" id="ks-meal-filter-method-label">用餐方式<span class="ks-required">多选，不选不限</span></span>
          <div class="ks-choices">${methods.map(m=>chip(mealMethodLabel(m),f.methods.includes(m),`data-meal-filter-method="${escapeText(m)}"`)).join('')}${chip('未填写方式',f.methods.includes('none'),'data-meal-filter-method="none"')}</div>
        </div>
        <div class="ks-selection-section" role="group" aria-labelledby="ks-meal-filter-prep-label"><span class="ks-label" id="ks-meal-filter-prep-label">制作分类<span class="ks-required">多选，不选不限</span></span>
          <div class="ks-choices">${preparations.map(c=>chip(mealPreparationLabel(c),f.preparations.includes(c),`data-meal-filter-prep="${escapeText(c)}"`)).join('')}</div>
        </div>
        <p class="ks-note" style="margin:0">不同条件同时满足；未选择的条件不限制。</p>`;
      const footer=`<p class="ks-error" role="alert" data-meal-filter-error></p>
        <div class="ks-selection-footer-actions">
          <button type="button" class="ks-choice cursor-interaction" data-action="reset-meal-filter">重置条件</button>
          <button type="submit" class="ks-primary cursor-interaction">应用筛选</button>
        </div>`;
      return selectionPanel('筛选用餐',body,footer,{form:true,attrs:'data-meal-filter-form'});
    }

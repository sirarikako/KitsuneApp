    let settingsScrollTop=0;
    function render(){
      const previous=rendered,kind=modalKind(),key=localKey(),changingPage=previous&&previous.page!==state.page;
      if(previous?.page==='settings')settingsScrollTop=phone.querySelector('.ks-settings-screen .ks-main').scrollTop;
      const keepingMealForm=previous?.page==='meal-form'&&state.page==='meal-form';
      const mealPosition=keepingMealForm?phone.querySelector('.ks-main').scrollTop:0;
      const oldLedger=phone.querySelector('.ks-ledger-screen');
      const sameLedger=oldLedger&&state.page==='ledger'&&previous.local===key;
      const ledgerPosition=sameLedger?oldLedger.querySelector('.ks-main').scrollTop:0;
      const ledgerCollapse=sameLedger?[...oldLedger.querySelectorAll('.ks-ledger-collapsible')].reduce(
        (sum,part)=>sum+part.firstElementChild.offsetHeight-part.offsetHeight,0):0;
      const ledgerCompact=sameLedger&&!!oldLedger.querySelector('.ks-fab-compact');
      // Measure before removing old nodes, but first clear any stale visual copies.
      stopMotion();
      const oldEditor=phone.querySelector('[data-appearance-editor]');
      const editorWasVisible=!!oldEditor&&!oldEditor.hidden;
      const editorHeight=editorWasVisible?oldEditor.getBoundingClientRect().height:0;
      const editorCopy=editorWasVisible&&state.page==='appearance'&&!appearanceChanged(pendingAppearance())?cloneVisual(oldEditor):null;
      const oldPage=changingPage&&!reducedMotion.matches?ghost([pageNode()].filter(Boolean)):null;
      const leavingPrimary=changingPage&&primary(previous.page)&&!primary(state.page);
      const oldNav=leavingPrimary&&!reducedMotion.matches?ghost([phone.querySelector(':scope > .ks-nav')]):null;
      const oldHeight=oldNav?phone.getBoundingClientRect().height:0;
      const oldModal=previous?.modal&&!kind&&!reducedMotion.matches?ghost([modalNode()].filter(Boolean),true):null;
      const localChanged=previous&&!changingPage&&key!==previous.local;
      const oldLocal=localChanged&&!reducedMotion.matches?ghost(localNodes()):null;
      const sourceRect=entrance;entrance=null;if(leavingPrimary&&!sourceRect)recordOrigin=null;
      rendering=true;
      const form=!primary(state.page);
      const titles={home:'日常记录',meals:'用餐',ledger:'记账',settings:'设置',appearance:'外观与调色板','wallet-edit':'编辑钱包', 'meal-form':`${state.editing?'编辑':'记录'}${slots[state.slot]}`,'meal-filter':'筛选用餐',entry:'记一笔','entry-detail':'账目详情','category-manager':'分类管理','category-owned':'钱包专属分类','category-detail':state.editingFinancialCategoryId?'编辑分类':'新增分类',github:'GitHub 备份'};
      const heading=`<h2 class="ks-title">${titles[state.page]}</h2>`;
      const header=state.page==='home'?`<div class="ks-home-brand"><span class="ks-app-mark"><img src="__KITSUNE_FOX_MARK__" alt=""></span><div>${heading}<p class="ks-sub">11 月 17 日 · 星期二</p></div></div>`:form?`<button type="button" class="ks-icon-btn cursor-interaction" data-action="back" aria-label="返回">${icon('arrow-left')}</button>${heading}`:heading;
      const pages={home:pageHome,meals:pageMeals,ledger:pageLedger,settings:pageSettings,appearance:pageAppearance,'wallet-edit':pageWalletEdit,'meal-form':pageMealForm,'meal-filter':pageMealFilter,entry:pageEntry,'entry-detail':pageEntryDetail,'category-manager':pageCategoryManager,'category-owned':pageOwnedCategories,'category-detail':pageCategoryDetail,github:pageGitHubBackup};
      const ledger=state.page==='ledger',fab=ledger&&state.ledgerTab==='流水';
      const settingsScreen=state.page==='settings'||state.page==='github'?`ks-${state.page}-screen`:'';
      const top=`<header class="ks-top ${form?'ks-subpage':''}">${header}</header>`;
      phone.innerHTML=`<div class="ks-status"><span>9:41</span><span class="ks-signal">${icon('signal')}${icon('wifi')}${icon('battery-full')}</span></div><div class="ks-page-motion ${settingsScreen} ${ledger?'ks-ledger-screen':''} ${fab?'ks-ledger-with-fab':''}">${ledger?`<div class="ks-ledger-collapsible">${top}</div>${ledgerTabs()}${ledgerFilters()}`:top}<main class="ks-main">${pages[state.page]()}${state.notice?`<p class="ks-success" role="status">${escapeText(state.notice)}</p>`:''}</main>${fab?`<button type="button" class="ks-primary ks-ledger-fab" data-action="add-entry" aria-label="记一笔">${icon('plus')}<span>记一笔</span></button>`:''}</div>${form?'':`<nav class="ks-nav" aria-label="主导航">${navs.map(([id,name,ic])=>`<button type="button" data-page="${id}" class="cursor-interaction" ${state.page===id?'aria-current="page"':''}><span>${icon(ic)}</span>${name}</button>`).join('')}</nav>`}<div class="ks-home-indicator" aria-hidden="true"></div>`;
      if(state.calcOpen){phone.insertAdjacentHTML('beforeend',calculatorSheet());phone.querySelector('.ks-top').inert=true;phone.querySelector('.ks-main').inert=true;phone.querySelector('[data-calc-action=cancel]').focus({preventScroll:true});}
      if(state.deletePending){phone.insertAdjacentHTML('beforeend',deleteConfirmation());phone.querySelector('.ks-top').inert=true;phone.querySelector('.ks-main').inert=true;phone.querySelector('[data-delete-action=cancel]').focus({preventScroll:true});}
      if(state.walletPickerOpen){phone.insertAdjacentHTML('beforeend',walletRangeDialog());for(const child of phone.children)if(!child.classList.contains('ks-modal-overlay'))child.inert=true;phone.querySelector('[data-wallet-mode][aria-pressed=true]').focus({preventScroll:true});}
      if(state.periodDraft){
        phone.insertAdjacentHTML('beforeend',periodDialog());
        for(const child of phone.children)if(!child.classList.contains('ks-modal-overlay'))child.inert=true;
        if(previous?.modal!==kind)phone.querySelector('[data-period-mode][aria-pressed=true],[data-period-month][aria-pressed=true]').focus({preventScroll:true});
      }
      if(state.ledgerFilterDraft){
        phone.insertAdjacentHTML('beforeend',ledgerFiltersDialog());
        for(const child of phone.children)if(!child.classList.contains('ks-modal-overlay'))child.inert=true;
        phone.querySelector('[data-ledger-filters] input').focus({preventScroll:true});
      }
      if(state.githubDemo.review){
        phone.insertAdjacentHTML('beforeend',githubDemoDialog());
        for(const child of phone.children)if(!child.classList.contains('ks-modal-overlay'))child.inert=true;
        phone.querySelector('[data-github-action=cancel]').focus({preventScroll:true});
      }
      if(state.mealCalendar){
        phone.insertAdjacentHTML('beforeend',mealCalendarDialog());
        for(const child of phone.children)if(!child.classList.contains('ks-modal-overlay'))child.inert=true;
        if(previous?.modal!==kind)phone.querySelector('[data-calendar-action=jump],[data-calendar-text]').focus({preventScroll:true});
      }
      if(state.entryTimeDraft){
        phone.insertAdjacentHTML('beforeend',entryTimeDialog());
        for(const child of phone.children)if(!child.classList.contains('ks-modal-overlay'))child.inert=true;
        const focus=state.entryTimeDraft.clock?'[data-time-part][aria-pressed=true]':'[data-entry-time] [name=hour]';
        phone.querySelector(focus).focus({preventScroll:true});
      }
      if(state.addingEntryCategory){
        phone.insertAdjacentHTML('beforeend',entryCategoryDialog());
        for(const child of phone.children)if(!child.classList.contains('ks-modal-overlay'))child.inert=true;
        phone.querySelector('[data-entry-category] [name=categoryName]').focus({preventScroll:true});
      }
      if(state.page==='settings')phone.querySelector('.ks-settings-screen .ks-main').scrollTop=settingsScrollTop;
      if(keepingMealForm)phone.querySelector('.ks-main').scrollTop=mealPosition;
      if(state.addingMealOption){
        phone.insertAdjacentHTML('beforeend',mealOptionDialog());
        for(const child of phone.children)if(!child.classList.contains('ks-modal-overlay'))child.inert=true;
        phone.querySelector('[data-meal-option-form] input').focus({preventScroll:true});
      }
      if(state.page==='appearance')updateAppearancePreview();
      globalThis.lucide?.createIcons({root:phone,attrs:{width:18,height:18}});
      if(ledger)attachLedgerScroll(ledgerPosition,ledgerCollapse,ledgerCompact);
      rendering=false;
      const previousStack=previous?.stack??[],pageIndex=previousStack.indexOf(state.page);
      const stack=primary(state.page)?[state.page]:pageIndex<0?[...previousStack,state.page]:previousStack.slice(0,pageIndex+1);
      rendered={page:state.page,modal:kind,local:key,stack};
      if(!reducedMotion.matches){
        if(changingPage)animatePage(previous,rendered,oldPage,sourceRect,oldNav,oldHeight);
        if(localChanged){attachGhost(oldLocal);play(oldLocal,outFade,motion.local,discard(oldLocal));for(const node of localNodes())play(node,inFade,motion.local);}
        animateModal(previous?.modal||null,oldModal);
        if(previous?.page==='appearance'&&state.page==='appearance'){
          const editor=phone.querySelector('[data-appearance-editor]');
          if(editorWasVisible&&editor.hidden)collapseAppearance(editor,editorCopy,editorHeight);
          else if(!editorWasVisible&&!editor.hidden)expandAppearance(editor);
        }
      }
      if(changingPage&&primary(state.page))recordOrigin=null;
      if(changingPage&&!kind){const header=phone.querySelector('.ks-top');header.setAttribute('tabindex','-1');header.focus({preventScroll:true});}
    }
    function capture(){const form=phone.querySelector('[data-meal-form]');if(form){state.dishes=form.elements.dishes.value;state.note=form.elements.note.value;state.date=form.elements.date.value;state.slot=Number(form.elements.slot.value);}const entry=phone.querySelector('[data-entry-form]');if(entry){state.entryDraft={dateTime:entry.elements.date.value+state.entryDraft.dateTime.slice(-6),note:entry.elements.note.value,category:entry.elements.category.value,walletId:entry.elements.wallet.value};}}
    function closeEntryTime(apply){
      const {hour,minute}=state.entryTimeDraft;
      if(apply){
        if(!validTimePart(hour,'hour')||!validTimePart(minute,'minute'))return;
        state.entryDraft.dateTime=state.entryDraft.dateTime.slice(0,-6)+'T'+hour+':'+minute;
      }
      state.entryTimeDraft=null;render();
      phone.querySelector('[data-action=open-entry-time]').focus({preventScroll:true});
    }
    function error(message){phone.querySelector('.ks-error').textContent=message;}
    function updateAppearancePreview(){
      const editor=phone.querySelector('[data-appearance-editor]');
      if(!editor)return;
      const wasVisible=!editor.hidden,show=appearanceChanged(pendingAppearance());
      if(!rendering&&wasVisible!==show){
        phone.querySelectorAll('.ks-motion-appearance-copy').forEach(node=>node.remove());
        editor.getAnimations().forEach(a=>a.cancel());
        if(!show){const height=editor.getBoundingClientRect().height,copy=cloneVisual(editor);editor.hidden=true;collapseAppearance(editor,copy,height);}
      }
      editor.hidden=!show;
      if(editor.hidden){state.previewMode=null;return;}
      if(!rendering&&!wasVisible)expandAppearance(editor);
      const preview=editor.querySelector('[data-appearance-preview]');
      const draft=state.appearanceDraft;
      const preferredMode=draft.mode==='system'?(systemDark.matches?'dark':'light'):draft.mode;
      const mode=state.previewMode||preferredMode;
      preview.style.colorScheme=mode;
      paintColors(preview,colorSeed(draft),draft.colorSource);
      for(const button of editor.querySelectorAll('[data-preview-mode]'))button.setAttribute('aria-pressed',String(button.dataset.previewMode===mode));
    }
    function pageEntryDetail(){const e=financialEntries().find(e=>e.id===state.selectedEntryId);if(!e)return '<p class="ks-empty-state">这笔账目已不存在</p>';const w=walletById(e.walletId);return `<section class="ks-entry-detail"><div class="ks-detail-amount"><span class="ks-note">${e.type} · ${w.code}</span><div class="ks-amount ${e.type==='支出'?'ks-expense':'ks-income'}">${e.type==='支出'?'−':'+'}${money(e.cents,w.code)}</div></div><dl class="ks-detail-list">${[['日期时间',`${e.date} ${e.time}`],['钱包',`${w.name} · ${w.code}`],['分类',entryCategory(e)],['备注',e.note||'无']].map(([label,value])=>`<div><dt>${label}</dt><dd>${escapeText(value)}</dd></div>`).join('')}</dl>${deleteButton('entry','删除这笔账目')}<p class="ks-footnote">交互预览 · 示例数据</p></section>`;}
    function deleteConfirmation(){const p=state.deletePending;return `<div class="ks-modal-overlay ks-delete-overlay"><section class="ks-delete-sheet" role="alertdialog" aria-modal="true" aria-label="确认删除记录"><h3>${p.kind==='meal'?'删除这条用餐记录？':'删除这笔账目？'}</h3><p class="ks-delete-summary">${escapeText(p.summary)}</p><p class="ks-note">${p.kind==='meal'?'删除后，将更新今日进度、月度及全部记录统计。':'删除后，将更新流水、收支统计和钱包余额。'}</p><p class="ks-error" role="alert" data-delete-error></p><div class="ks-calc-actions"><button type="button" class="ks-choice cursor-interaction" data-delete-action="cancel">取消</button><button type="button" class="ks-primary ks-danger-confirm cursor-interaction" data-delete-action="confirm">确认删除</button></div></section></div>`;}
    function cancelDelete(){const action=state.deletePending.kind==='meal'?'delete-meal':'delete-entry';state.deletePending=null;render();phone.querySelector(`[data-action=${action}]`)?.focus({preventScroll:true});}
    function confirmDelete(){const p=state.deletePending;if(p.kind==='meal'){if(state.records[p.date])state.records[p.date][p.slot]=null;state.date=p.date;state.page=state.mealReturn;state.editing=false;}else{if(financialEntries().some(e=>e.originalExpenseId===p.id)){phone.querySelector('[data-delete-error]').textContent='请先删除这笔支出关联的退款，再删除原支出。';return;}state.deletedEntryIds.add(p.id);state.page='ledger';state.ledgerTab='流水';}state.deletePending=null;state.notice='已从当前演示删除记录。';render();}
    function closeCalculator(apply){if(apply){try{state.amount=inputAmount(calcCents(state.calcExpression));}catch(e){phone.querySelector('.ks-calc-error').textContent=e.message;return;}}state.calcOpen=false;render();phone.querySelector('[data-action=open-calculator]').focus({preventScroll:true});}
    function closeLedgerFilters(apply){
      if(apply){
        const note=phone.querySelector('[data-ledger-filters] [name=note]').value;
        state.ledgerFilters={...state.ledgerFilterDraft,note};
      }
      state.ledgerFilterDraft=null;render();
      phone.querySelector('[data-action=open-ledger-filters]')?.focus({preventScroll:true});
    }
    function closeWalletPicker(action){
      if(action==='apply'){
        const mode=state.walletDraftMode;
        if(mode==='MANUAL'&&!state.walletDraftIds.length)return;
        state.walletFilterIds=mode==='MANUAL'?[...state.walletDraftIds]:mode==='ALL'?'ALL':null;
      }else if(action==='default')state.walletFilterIds=null;
      state.walletPickerOpen=false;render();persist();phone.querySelector('[data-action=choose-wallets]')?.focus({preventScroll:true});
    }
    function closeMealOption(){
      const kind=state.addingMealOption;
      state.addingMealOption=null;render();
      phone.querySelector(`[data-add-meal-option="${kind}"]`)?.focus({preventScroll:true});
    }
    function closeEntryCategory(){
      state.addingEntryCategory=false;render();
      phone.querySelector('[data-action=add-entry-category]')?.focus({preventScroll:true});
    }
    function closeMealCalendar(apply){
      const d=state.mealCalendar;
      if(apply){
        const date=d.mode==='text'?d.text:d.selected;
        if(d.mode==='jump'||!isDate(date))return;
        state.date=date;
      }
      state.mealCalendar=null;render();
      phone.querySelector('[data-action=open-meal-calendar]').focus({preventScroll:true});
    }
    function closePeriod(apply){
      const draft=state.periodDraft,mealStats=draft.target==='meals';
      if(apply){
        if(!canApplyPeriod(draft))return;
        if(draft.mode==='month'){
          const month=`${String(Number(draft.year)).padStart(4,'0')}-${draft.month.padStart(2,'0')}`;
          if(mealStats)state.mealStatsFilters={...state.mealStatsFilters,...mealMonthDates(month)};
          else{
            state.ledgerMonth=month;
            state.ledgerRange=null;
          }
        }else state.ledgerRange={from:draft.from,to:draft.to};
      }
      state.periodDraft=null;render();
      phone.querySelector(mealStats?'[data-action=choose-meal-month]':'[data-action=choose-ledger-period]')?.focus({preventScroll:true});
      if(apply)persist();
    }
        function calcCents(expression){
      const source=expression.replace(/×/g,'*').replace(/[−–]/g,'-').replace(/÷/g,'/').replace(/\s/g,'');
      if(source.length>80||!/^\d+(?:\.\d*)?(?:[+*/-]\d+(?:\.\d*)?)*$/.test(source))throw Error('请补全金额或计算式，例如 12+8×2。');
      const tokens=source.match(/\d+(?:\.\d*)?|[+*/-]/g);
      const parse=s=>{const [a,b='']=s.split('.');return [BigInt(a+b),10n**BigInt(b.length)];};
      const op=(a,b,o)=>{if(o==='+')return[a[0]*b[1]+b[0]*a[1],a[1]*b[1]];if(o==='-')return[a[0]*b[1]-b[0]*a[1],a[1]*b[1]];if(o==='*')return[a[0]*b[0],a[1]*b[1]];if(!b[0])throw Error('不能除以 0，请修改计算式。');return[a[0]*b[1],a[1]*b[0]];};
      const sums=[];let term=parse(tokens[0]);for(let i=1;i<tokens.length;i+=2){if(tokens[i]==='*'||tokens[i]==='/')term=op(term,parse(tokens[i+1]),tokens[i]);else{sums.push(term,tokens[i]);term=parse(tokens[i+1]);}}sums.push(term);let result=sums[0];for(let i=1;i<sums.length;i+=2)result=op(result,sums[i+1],sums[i]);
      if(result[0]<=0n)throw Error('金额需大于 0。');
      if(!entryWallet())throw Error('请先选择钱包。');const factor=10n**BigInt(entryWallet().scale),cents=(result[0]*factor*2n+result[1])/(result[1]*2n);
      if(cents<=0n)throw Error('金额四舍五入后必须大于 0。');
      if(cents>maximumEntryMinor)throw Error('超出单笔金额上限，请减小金额。');
      return cents;
    }

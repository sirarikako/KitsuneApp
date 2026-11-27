    let settingsScrollTop=0;
    function render(){
      const previous=rendered,kind=modalKind(),key=localKey(),changingPage=previous&&previous.page!==state.page;
      if(previous?.page==='settings')settingsScrollTop=phone.querySelector('.ks-settings-screen .ks-main').scrollTop;
      const keepingForm=previous?.page===state.page&&['meal-form','meal-filter','entry','wallet-edit'].includes(state.page);
      const formPosition=keepingForm?phone.querySelector('.ks-main').scrollTop:0;
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
      const titles={home:'日常记录',meals:'用餐',ledger:'记账',settings:'设置',appearance:'外观与调色板','local-backup':'本地备份与恢复','csv-transfer':'CSV 导入与导出','wallet-edit':'编辑钱包', 'meal-form':`${state.editing?'编辑':'记录'}${slots[state.slot]}`,'meal-filter':'筛选用餐',entry:'记一笔','entry-detail':'账目详情','category-manager':'分类管理','category-owned':'钱包专属分类','category-detail':state.editingFinancialCategoryId?'编辑分类':'新增分类',github:'GitHub 备份'};
      const heading=`<h2 class="ks-title">${titles[state.page]}</h2>`;
      const header=state.page==='home'?`<div class="ks-home-brand"><span class="ks-app-mark"><img src="__KITSUNE_FOX_MARK__" alt=""></span><div>${heading}<p class="ks-sub">11 月 17 日 · 星期二</p></div></div>`:form?`<button type="button" class="ks-icon-btn cursor-interaction" data-action="back" aria-label="返回">${icon('arrow-left')}</button>${heading}`:heading;
      const pages={home:pageHome,meals:pageMeals,ledger:pageLedger,settings:pageSettings,appearance:pageAppearance,'local-backup':pageLocalBackup,'csv-transfer':pageCsvTransfer,'wallet-edit':pageWalletEdit,'meal-form':pageMealForm,'meal-filter':pageMealFilter,entry:pageEntry,'entry-detail':pageEntryDetail,'category-manager':pageCategoryManager,'category-owned':pageOwnedCategories,'category-detail':pageCategoryDetail,github:pageGitHubBackup};
      const ledger=state.page==='ledger',fab=ledger&&state.ledgerTab==='流水';
      const settingsScreen=state.page==='settings'||state.page==='github'?`ks-${state.page}-screen`:'';
      const top=`<header class="ks-top ${form?'ks-subpage':''}">${header}</header>`;
      phone.innerHTML=`<div class="ks-status"><span>9:41</span><span class="ks-signal">${icon('signal')}${icon('wifi')}${icon('battery-full')}</span></div><div class="ks-page-motion ${settingsScreen} ${ledger?'ks-ledger-screen':''} ${fab?'ks-ledger-with-fab':''}">${ledger?`<div class="ks-ledger-collapsible">${top}</div>${ledgerTabs()}${ledgerFilters()}`:top}<main class="ks-main">${pages[state.page]()}${state.notice?`<p class="ks-success" role="status">${escapeText(state.notice)}</p>`:''}</main>${fab?`<button type="button" class="ks-primary ks-ledger-fab" data-action="add-entry" aria-label="记一笔">${icon('plus')}<span>记一笔</span></button>`:''}</div>${form?'':`<nav class="ks-nav" aria-label="主导航">${navs.map(([id,name,ic])=>`<button type="button" data-page="${id}" class="cursor-interaction" ${state.page===id?'aria-current="page"':''}><span>${icon(ic)}</span>${name}</button>`).join('')}</nav>`}<div class="ks-home-indicator" aria-hidden="true"></div>`;
      if(state.calcOpen){phone.insertAdjacentHTML('beforeend',calculatorSheet());phone.querySelector('.ks-top').inert=true;phone.querySelector('.ks-main').inert=true;updateCalculatorReadout();phone.querySelector('[data-calc-action=cancel]').focus({preventScroll:true});}
      if(state.deletePending){phone.insertAdjacentHTML('beforeend',deleteConfirmation());phone.querySelector('.ks-top').inert=true;phone.querySelector('.ks-main').inert=true;phone.querySelector('[data-delete-action=cancel]').focus({preventScroll:true});}
      if(state.walletPickerOpen||state.ledgerFilterDraft||state.singleSelection){
        const panel=state.walletPickerOpen?walletRangeDialog():state.ledgerFilterDraft?ledgerFiltersDialog():singleSelectionPanel();
        phone.insertAdjacentHTML('beforeend',panel);
        for(const child of phone.children)if(!child.classList.contains('ks-modal-overlay'))child.inert=true;
        phone.querySelector('.ks-selection-panel [data-select-close]').focus({preventScroll:true});
      }
      if(state.periodDraft){
        phone.insertAdjacentHTML('beforeend',periodDialog());
        for(const child of phone.children)if(!child.classList.contains('ks-modal-overlay'))child.inert=true;
        if(previous?.modal!==kind)phone.querySelector('[data-period-mode][aria-pressed=true],[data-period-month][aria-pressed=true]').focus({preventScroll:true});
      }
      if(state.githubDemo.review){
        phone.insertAdjacentHTML('beforeend',githubDemoDialog());
        for(const child of phone.children)if(!child.classList.contains('ks-modal-overlay'))child.inert=true;
        phone.querySelector('[data-github-action=cancel]').focus({preventScroll:true});
      }
      if(state.localBackupDemo.restoreOpen){
        phone.insertAdjacentHTML('beforeend',localRestoreDialog());
        for(const child of phone.children)if(!child.classList.contains('ks-modal-overlay'))child.inert=true;
        phone.querySelector('[data-local-backup-action=close-restore]').focus({preventScroll:true});
      }
      if(state.csvDemo.previewOpen&&!state.singleSelection){
        phone.insertAdjacentHTML('beforeend',csvPreviewDialog());
        for(const child of phone.children)if(!child.classList.contains('ks-modal-overlay'))child.inert=true;
        phone.querySelector('[data-csv-action=close-preview]').focus({preventScroll:true});
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
      if(keepingForm)phone.querySelector('.ks-main').scrollTop=formPosition;
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
    function capture(){
      const form=phone.querySelector('[data-meal-form]');
      if(form){
        state.dishes=form.elements.dishes.value;state.note=form.elements.note.value;
        state.date=form.elements.date.value;state.slot=Number(form.elements.slot.value);
      }
      const entry=phone.querySelector('[data-entry-form]');
      if(entry){
        state.entryDraft.dateTime=entry.elements.date.value+state.entryDraft.dateTime.slice(-6);
        state.entryDraft.note=entry.elements.note.value;
      }
      const meal=phone.querySelector('[data-meal-filter-form]');
      if(meal){
        const {from,to,slot,atHome,keyword}=meal.elements;
        state.mealFilterDraft={...state.mealFilterDraft,from:from.value,to:to.value,slot:slot.value,atHome:atHome.value,
          keyword:keyword.value,preparations:[...meal.querySelectorAll('[name=preparation]:checked')].map(e=>e.value)};
      }
      if(state.page==='wallet-edit'){
        const draft=state.walletEditDraft;
        draft.separateAccounting=phone.querySelector('[data-wallet-independent]').checked;
        draft.defaultWallet=phone.querySelector('[data-wallet-default]').checked;
        draft.defaultToIncome=phone.querySelector('[data-wallet-income-default]').checked;
      }
    }
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
    const calcOperators='+−×÷';
    function openCalculator(){
      if(!entryWallet()||modalKind())return;
      state.calcExpression=state.amount;state.calcResult=null;state.calcOpen=true;render();
    }
    function closeCalculator(apply){
      if(apply){
        try{state.amount=inputAmount(calcCents(state.calcExpression));}
        catch(failure){phone.querySelector('.ks-calc-error').textContent=failure.message;return;}
      }
      stopCalcBackspace();
      state.calcOpen=false;render();phone.querySelector('[data-action=open-calculator]')?.focus({preventScroll:true});
    }
    function updateCalculatorReadout(){
      const expression=state.calcExpression,hasOperation=[...expression].some(char=>calcOperators.includes(char));
      let result=state.calcResult;
      if(result===null){
        result=hasOperation?'':expression||inputAmount(0n);
        if(hasOperation){
          try{result=inputAmount(calcCents(expression));}catch{}
        }
      }
      for(const [selector,value] of [
        ['[data-calc-expression]',hasOperation||(state.calcResult!==null&&state.calcResult!==expression)?expression:''],
        ['[data-calc-result]',result],
      ]){
        const readout=phone.querySelector(selector);
        readout.textContent=value;
        readout.scrollLeft=readout.scrollWidth;
        readout.tabIndex=readout.scrollWidth>readout.clientWidth?0:-1;
      }
      phone.querySelector('[data-calc-action=apply]').disabled=expression.length===0;
    }
    function pressCalculatorKey(key){
      const error=phone.querySelector('.ks-calc-error');
      error.textContent='';
      if(key==='='){
        try{state.calcResult=inputAmount(calcCents(state.calcExpression));phone.querySelector('.ks-calc-body').scrollTop=0;}
        catch(failure){error.textContent=failure.message;return;}
      }else{
        if(key==='C')state.calcExpression='';
        else if(key==='⌫')state.calcExpression=state.calcExpression.slice(0,-1);
        else if(state.calcResult!==null)state.calcExpression=calcOperators.includes(key)?state.calcResult+key:key;
        else state.calcExpression=(state.calcExpression+key).slice(0,512);
        state.calcResult=null;
      }
      updateCalculatorReadout();
    }
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
      }
      state.walletPickerOpen=false;render();persist();phone.querySelector('[data-action=choose-wallets]')?.focus({preventScroll:true});
    }
    function closeSingleSelection(value){
      const kind=state.singleSelection;
      if(value!==undefined){
        switch(kind){
          case 'entry-wallet': {
            const {category,walletId}=state.entryDraft;
            if(walletId!==value){
              state.entryDraft.walletId=value;
              const nextType=state.followsWalletType?defaultEntryType(entryWallet()):state.entryType;
              state.entryDraft.category=nextType!==state.entryType||!category
                ?initialEntryCategory(value,nextType):chooseFinancialCategory(value,nextType,category);
              state.entryType=nextType;
            }
            break;
          }
          case 'entry-category':
            state.entryDraft.category=value;state.followsWalletType=false;break;
          case 'meal-method':
            state.mealFilterDraft.method=value;break;
          case 'category-wallet':
            state.categoryWalletId=value;state.editingFinancialCategoryId=null;break;
          case 'csv-method':
            state.csvMethod=value;break;
          case 'csv-wallet':
            state.csvWallet=value;break;
          case 'currency':
            state.walletEditDraft.code=value;break;
        }
      }
      state.singleSelection=null;render();
      phone.querySelector(`[data-select-open="${kind}"]`)?.focus({preventScroll:true});
    }
    function cancelSelection(){
      if(state.singleSelection)closeSingleSelection();
      else if(state.walletPickerOpen)closeWalletPicker('cancel');
      else if(state.ledgerFilterDraft)closeLedgerFilters(false);
    }
    function closeMealOption(){
      const kind=state.addingMealOption;
      state.addingMealOption=null;render();
      phone.querySelector(`[data-add-meal-option="${kind}"]`)?.focus({preventScroll:true});
    }
    function closeEntryCategory(){
      state.addingEntryCategory=false;render();
      phone.querySelector('[data-select-open=entry-category]')?.focus({preventScroll:true});
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
      if(!expression.length||expression.length>512)throw Error('请输入不超过 512 字符的表达式');
      const source=expression.replaceAll('×','*').replaceAll('−','-').replaceAll('÷','/');
      const number=/\s*([+-]?[0-9.]+)/y;
      const decimal=/^[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)$/;
      let index=0;
      const peek=()=>{
        while(/\s/.test(source[index]??''))index++;
        return source[index]??'';
      };
      const readNumber=()=>{
        number.lastIndex=index;
        const match=number.exec(source);
        if(!match||!decimal.test(match[1]))throw Error('表达式缺少有效数字');
        index=number.lastIndex;
        const [a,b='']=match[1].split('.');
        return [BigInt(a+b),10n**BigInt(b.length)];
      };
      const readTerm=()=>{
        let [n,d]=readNumber();
        for(let op=peek();op==='*'||op==='/';op=peek()){
          index++;
          const [p,q]=readNumber();
          if(op==='*'){n*=p;d*=q;}
          else{
            if(!p)throw Error('除数不能为零');
            n*=p<0n?-q:q;
            d*=p<0n?-p:p;
          }
        }
        return [n,d];
      };
      let [n,d]=readTerm();
      for(let op=peek();op==='+'||op==='-';op=peek()){
        index++;
        const [p,q]=readTerm();
        n=n*q+(op==='+'?p:-p)*d;d*=q;
      }
      if(peek())throw Error('表达式包含无效字符');
      if(!entryWallet())throw Error('请先选择钱包。');
      const scaled=n*10n**BigInt(entryWallet().scale);
      const cents=(scaled*2n+(scaled<0n?-d:d))/(d*2n);
      if(cents>maximumEntryMinor||cents< -maximumEntryMinor-1n)throw Error('单笔金额超出 64 位范围');
      if(cents<=0n)throw Error('舍入后金额必须大于零');
      return cents;
    }

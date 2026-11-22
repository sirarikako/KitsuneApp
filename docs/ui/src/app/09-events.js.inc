    phone.addEventListener('click',event=>{
      const b=event.target.closest('button');if(!b)return;
      capture();state.notice='';
      if(b.dataset.previewMode){state.previewMode=b.dataset.previewMode;updateAppearancePreview();return;}
      if(b.dataset.action==='open-appearance'){resetAppearance();state.page='appearance';render();return;}
      if(b.dataset.action==='reset-appearance'){state.appearanceDraft={...defaultAppearance};render();return;}
      if(b.dataset.action==='cancel-appearance'){resetAppearance();render();return;}
      if(b.dataset.action==='apply-appearance'){
        const next=pendingAppearance();
        if(!appearanceChanged(next))return;
        if(next.customColor===null){phone.querySelector('[data-appearance-error]').textContent='请输入六位 HEX 颜色，例如 #C47A4A。';return;}
        applyAnimatedAppearance(next);state.notice='已应用外观。';render();persist();return;
      }
      if(b.dataset.action==='edit-ledger-range'){
        state.ledgerRangeOpen=!state.ledgerRangeOpen;render();
        phone.querySelector(state.ledgerRangeOpen?'[data-ledger-range-form] [name=from]':'[data-action=edit-ledger-range]').focus();
        return;
      }
      if(b.dataset.action==='cancel-ledger-range'){state.ledgerRangeOpen=false;render();phone.querySelector('[data-action=edit-ledger-range]').focus();return;}
      if(b.dataset.action==='ledger-month-mode'){state.ledgerRange=null;state.ledgerRangeOpen=false;render();phone.querySelector('[data-month-input=ledgerMonth]').focus();persist();return;}
      if(b.dataset.action==='meal-filter'){state.mealFilterDraft={...state.mealFilters,preparations:[...state.mealFilters.preparations]};state.page='meal-filter';render();return;}
      if(b.dataset.action==='reset-meal-filter'){state.mealFilterDraft=blankMealFilters();render();return;}
      if(b.dataset.action==='meal-daily'){state.mealListMode=false;render();persist();return;}
      if(b.dataset.action==='clear-meal-filters'){state.mealFilters=blankMealFilters();render();persist();return;}
      if(b.dataset.action==='manage-categories'){state.categoryReturn=state.page;state.categoryWalletId=b.dataset.categoryWallet||'GLOBAL';state.financialScope='EXPENSE';state.editingFinancialCategoryId=null;state.page='category-manager';render();return;}
      if(b.dataset.financialScope){state.financialScope=b.dataset.financialScope;state.editingFinancialCategoryId=null;render();return;}
      if(b.dataset.editFinancialCategory){state.editingFinancialCategoryId=b.dataset.editFinancialCategory;render();phone.querySelector('[data-financial-category-form] [name=categoryName]')?.focus({preventScroll:true});return;}
      if(b.dataset.archiveFinancialCategory){const c=categoryById(b.dataset.archiveFinancialCategory);if(c&&!c.isDefault){c.archived=!c.archived;state.editingFinancialCategoryId=null;render();}return;}
      if(b.dataset.action==='cancel-category-edit'){state.editingFinancialCategoryId=null;render();return;}
      if(b.dataset.action==='edit-general-categories'){state.categoryWalletId='GLOBAL';state.editingFinancialCategoryId=null;render();return;}
      if(b.dataset.action==='choose-wallets'){state.walletDraftIds=selectedWallets().map(w=>w.id);state.walletPickerOpen=true;render();return;}
      if(b.dataset.editWallet){state.editingWalletId=b.dataset.editWallet;state.page='wallet-edit';render();return;}
      if(b.dataset.action==='save-wallet'){walletById(state.editingWalletId).separateAccounting=phone.querySelector('[data-wallet-independent]').checked;state.page='ledger';render();persist();return;}
      if(b.dataset.walletAction){closeWalletPicker(b.dataset.walletAction);return;}
      if(b.dataset.entryId){if(!financialEntries().some(e=>e.id===b.dataset.entryId))return;recordOrigin={page:state.page,id:b.dataset.entryId};entrance=visible(b)?rect(b):null;state.selectedEntryId=b.dataset.entryId;state.page='entry-detail';render();return;}
      if(b.dataset.action==='delete-meal'){const meal=state.records[state.editingDate]?.[state.editingSlot];if(!meal)return;state.deletePending={kind:'meal',date:state.editingDate,slot:state.editingSlot,summary:`${state.editingDate} · ${slots[state.editingSlot]}\n${meal.dishes||'无菜品内容'}`};render();return;}
      if(b.dataset.action==='delete-entry'){const e=financialEntries().find(e=>e.id===state.selectedEntryId);if(!e)return;const w=walletById(e.walletId);state.deletePending={kind:'entry',id:e.id,summary:`${e.date} ${e.time}\n${w.name} · ${w.code}\n${entryCategory(e)} · ${e.type} · ${e.type==='支出'?'−':'+'}${money(e.cents,w.code)} ${w.code}`};render();return;}
      if(b.dataset.deleteAction){if(b.dataset.deleteAction==='confirm')confirmDelete();else cancelDelete();return;}
      if(b.dataset.action==='open-calculator'){state.calcExpression=state.amount;state.calcOpen=true;render();return;}
      if(b.dataset.calcAction){closeCalculator(b.dataset.calcAction==='apply');return;}
      if(b.dataset.monthStep){const key=b.dataset.monthKey;if(!['ledgerMonth','mealMonth'].includes(key))return;const [year,month]=state[key].split('-').map(Number),ordinal=year*12+month-1+Number(b.dataset.monthStep);if(ordinal<12||ordinal>=120000)return;state[key]=`${String(Math.floor(ordinal/12)).padStart(4,'0')}-${String(ordinal%12+1).padStart(2,'0')}`;if(key==='ledgerMonth')state.ledgerRangeOpen=false;render();persist();return;}
      if(b.dataset.page){if(state.page===b.dataset.page)return;state.page=b.dataset.page;state.ledgerRangeOpen=false;if(state.page==='settings')resetAppearance();render();persist();return;}
      if(b.dataset.date){state.date=b.dataset.date;render();return;}
      if(b.dataset.meal!==undefined){if(state.page==='home')state.date='2026-11-17';else if(b.dataset.mealDate)state.date=b.dataset.mealDate;state.slot=Number(b.dataset.meal);state.mealReturn=state.page;const m=records()[state.slot];state.editing=!!m;if(m&&b.classList.contains('ks-record')){recordOrigin={page:state.page,date:state.date,slot:b.dataset.meal};entrance=visible(b)?rect(b):null;}else recordOrigin=null;state.editingDate=state.date;state.editingSlot=state.slot;state.atHome=m?.atHome??null;state.method=m?.method??null;state.categories=[...(m?.categories||[])];state.dishes=m?.dishes||'';state.note=m?.note||'';state.page='meal-form';render();return;}
      if(b.dataset.homeChoice!==undefined){state.atHome=b.dataset.homeChoice==='true';render();return;}
      if(b.dataset.method){const m=b.dataset.method;state.method=state.method===m?null:m;if(state.method!=='下厨')state.categories=[];render();return;}
      if(b.dataset.category){const c=b.dataset.category;state.categories=state.categories.includes(c)?state.categories.filter(x=>x!==c):[...state.categories,c];render();return;}
      if(b.dataset.mealTab){state.mealTab=b.dataset.mealTab;render();return;}
      if(b.dataset.ledgerTab){state.ledgerTab=b.dataset.ledgerTab;state.ledgerRangeOpen=false;render();return;}
      if(b.dataset.entryType){state.entryType=b.dataset.entryType;state.entryDraft.category=chooseFinancialCategory(state.entryDraft.walletId,state.entryType,financialScopeOf(state.entryType)==='INCOME'?'gi-salary':'ge-food');render();return;}
      if(b.dataset.key!==undefined){const input=phone.querySelector('[data-calc-expression]'),k=b.dataset.key;state.calcExpression=input.value;phone.querySelector('.ks-calc-error').textContent='';if(k==='C')state.calcExpression='';else if(k==='⌫')state.calcExpression=state.calcExpression.slice(0,-1);else if(k==='='){try{state.calcExpression=inputAmount(calcCents(state.calcExpression));}catch(e){phone.querySelector('.ks-calc-error').textContent=e.message;return;}}else if(state.calcExpression.length<80)state.calcExpression+=k;input.value=state.calcExpression;return;}
      if(b.dataset.action==='add-entry'){state.page='entry';state.amount='';state.ledgerRangeOpen=false;state.entryType='支出';state.entryDraft={dateTime:`${demoToday}T09:41`,note:'',category:defaultEntryWallet()?'ge-food':'',walletId:defaultEntryWallet()?.id||''};render();return;}
      if(b.dataset.action==='back'){state.page=state.page==='category-manager'?state.categoryReturn:state.page==='appearance'?'settings':state.page==='meal-filter'?'meals':state.page==='meal-form'?state.mealReturn:'ledger';render();return;}
      if(b.dataset.action==='new-category'){phone.querySelector('[data-new-category]').hidden=false;phone.querySelector('[data-category-name]').focus();return;}
      if(b.dataset.action==='confirm-category'){const c=phone.querySelector('[data-category-name]').value.trim();if(c&&!state.categories.includes(c))state.categories.push(c);render();}
    });
    phone.addEventListener('keydown',event=>{if(!state.calcOpen&&!state.deletePending&&!state.walletPickerOpen)return;if(event.key==='Escape'){event.preventDefault();if(state.walletPickerOpen)closeWalletPicker('cancel');else if(state.deletePending)cancelDelete();else closeCalculator(false);}else if(state.calcOpen&&event.key==='Enter'&&event.target.matches('[data-calc-expression]')){event.preventDefault();closeCalculator(true);}else if(event.key==='Tab'){const dialog=phone.querySelector('[role=dialog],[role=alertdialog]'),controls=[...dialog.querySelectorAll('button,input')],first=controls[0],last=controls[controls.length-1];if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}}});
    phone.addEventListener('change',event=>{
      const e=event.target;
      if(e.matches('[data-category-wallet]')){state.categoryWalletId=e.value;state.editingFinancialCategoryId=null;render();return;}
      if(e.matches('[data-category-mode]')){const wallet=wallets.find(w=>w.id===state.categoryWalletId);if(wallet){wallet[state.financialScope==='INCOME'?'incomeCategoryMode':'expenseCategoryMode']=e.value;state.editingFinancialCategoryId=null;render();}return;}
      if(e.matches('[data-wallet-all]')){state.walletDraftIds=e.checked?wallets.map(w=>w.id):[];render();return;}
      if(e.matches('[data-wallet-option]')){const id=e.dataset.walletOption;state.walletDraftIds=e.checked?[...new Set([...state.walletDraftIds,id])]:state.walletDraftIds.filter(x=>x!==id);render();phone.querySelector(`[data-wallet-option=${id}]`).focus({preventScroll:true});return;}
      if(e.matches('[data-entry-form] [name=wallet]')){capture();state.entryDraft.category=chooseFinancialCategory(state.entryDraft.walletId,state.entryType,state.entryDraft.category);render();return;}
      if(e.matches('[data-month-input]')){const key=e.dataset.monthInput;if(['ledgerMonth','mealMonth'].includes(key)&&/^\d{4}-(0[1-9]|1[0-2])$/.test(e.value)&&e.value.slice(0,4)!=='0000'){state[key]=e.value;if(key==='ledgerMonth')state.ledgerRangeOpen=false;}render();persist();return;}
      if(e.matches('[data-theme]')){state.appearanceDraft.mode=e.value;updateAppearancePreview();return;}
      if(e.matches('[data-color-source]')){state.appearanceDraft.colorSource=e.value;render();return;}
      if(e.matches('[data-palette]')){state.appearanceDraft.presetColor=e.value;updateAppearancePreview();return;}
      if(e.matches('[data-date-input]')){state.date=isDate(e.value)?e.value:demoToday;render();}
      if(e.matches('[data-meal-form] [name=slot]')){capture();render();}
    });
    phone.addEventListener('input',event=>{
      const e=event.target;
      if(!e.matches('[data-custom-picker],[data-custom-hex]'))return;
      state.appearanceDraft.customColor=e.value;
      const normalized=normalizeHex(e.value);
      if(e.matches('[data-custom-picker]'))phone.querySelector('[data-custom-hex]').value=normalized;
      if(normalized){phone.querySelector('[data-custom-picker]').value=normalized;phone.querySelector('[data-appearance-error]').textContent='';}
      updateAppearancePreview();
    });
    phone.addEventListener('submit',event=>{
      event.preventDefault();const f=event.target;capture();
      if(f.matches('[data-financial-category-form]')){
        const name=f.elements.categoryName.value.trim(),scope=state.financialScope,owner=state.categoryWalletId;
        const items=owner==='GLOBAL'?sharedFinancialCategories[scope]:walletFinancialCategories[owner][scope];
        const edited=items.find(c=>c.id===state.editingFinancialCategoryId);
        const normalized=name.toLocaleLowerCase();
        if(!name||normalized==='未分类'||items.some(c=>c.id!==edited?.id&&c.name.trim().toLocaleLowerCase()===normalized)){error('分类名不能为空、不能使用“未分类”，且不能与本范围已有名称重复。');return;}
        if(edited)edited.name=name;else items.push({id:'user-cat-'+state.nextFinancialCategoryId++,name,archived:false});
        state.editingFinancialCategoryId=null;state.notice='演示分类已更新。';render();return;
      }
      if(f.matches('[data-ledger-range-form]')){
        const from=f.elements.from.value,to=f.elements.to.value;
        if(!isDate(from)||!isDate(to)||from>to){error('请输入有效日期，开始日期不能晚于结束日期。');return;}
        state.ledgerRange={from,to};state.ledgerRangeOpen=false;render();
        phone.querySelector('[data-action=edit-ledger-range]').focus();persist();return;
      }
      if(f.matches('[data-meal-filter-form]')){const from=f.elements.from.value,to=f.elements.to.value;if(from&&to&&from>to){error('开始日期不能晚于结束日期。');return;}state.mealFilters={from,to,slot:f.elements.slot.value,atHome:f.elements.atHome.value,method:f.elements.method.value,preparations:[...f.querySelectorAll('[name=preparation]:checked')].map(e=>e.value),keyword:f.elements.keyword.value.trim()};state.mealListMode=true;state.mealTab='记录';state.page='meals';render();persist();return;}
      if(f.matches('[data-meal-form]')){
        if(state.atHome===null){error('请先选择是否计划在家吃。');return;}
        if(state.atHome&&!state.method){error('计划在家吃时，请选择一种用餐方式。');return;}
        const list=records();if(list[state.slot]&&!(state.editing&&state.date===state.editingDate&&state.slot===state.editingSlot)){error('当天这一餐已有记录，请返回后编辑已有记录。');return;}
        if(state.editing&&(state.date!==state.editingDate||state.slot!==state.editingSlot))state.records[state.editingDate][state.editingSlot]=null;
        state.records[state.date]||=[null,null,null];state.records[state.date][state.slot]={atHome:state.atHome,method:state.method,categories:[...state.categories],dishes:state.dishes,note:state.note};state.page=state.mealReturn;state.notice='已保存到当前演示。';render();return;
      }
      if(f.matches('[data-entry-form]')){
        try{
          if(!f.elements.wallet.value){error('请先选择钱包。');return;}const cents=calcCents(f.elements.amount.value),walletId=f.elements.wallet.value;
          if(!effectiveFinancialCategories(walletId,state.entryType).some(c=>c.id===f.elements.category.value)){error('请选择当前钱包可用的分类。');return;}
          const entry={id:`draft-${state.nextEntryId++}`,type:state.entryType,cents,walletId,category:f.elements.category.value,note:f.elements.note.value,date:f.elements.dateTime.value.slice(0,10),time:f.elements.dateTime.value.slice(11,16)};
          state.entries.push(entry);if(!walletById(walletId).separateAccounting)state.lastWalletId=walletId;state.page='ledger';state.ledgerTab='流水';
          state.notice=matchesLedgerScope(entry)?'已保存到当前演示。':'已保存到当前演示，该账目不在当前筛选范围内。';
          render();
        }catch(e){error(e.message);}
      }
    });
    applyStyle();render();
    systemDark.addEventListener('change',updateAppearancePreview);
    const refreshHomeMeal=()=>{if(state.page==='home'&&phone.querySelector('[data-home-meal]')?.dataset.meal!==String(recommendedSlot()))render();};
    const mealTimer=setInterval(refreshHomeMeal,60000);
    window.addEventListener('focus',refreshHomeMeal);
    window.addEventListener('pagehide',()=>clearInterval(mealTimer),{once:true});
    if(globalThis.Tweak){const tweak=new Tweak({container:phone,onChange:()=>{applyStyle();if(state.page==='home')render();}});tweak.addColorPicker(style,'customColor',{label:'自选主色（自选模式）',reference:'--ks-seed'});tweak.addSelect(style,'mode',{label:'明暗模式',options:[{label:'跟随系统',value:'system'},{label:'浅色',value:'light'},{label:'深色',value:'dark'}]});tweak.addSelect(style,'mealHour',{label:'首页餐次预览',options:[{label:'跟随当前时间',value:'auto'},{label:'早餐时段',value:'8'},{label:'午餐时段',value:'12'},{label:'晚餐时段',value:'19'}]});}
  }
})();
</script>

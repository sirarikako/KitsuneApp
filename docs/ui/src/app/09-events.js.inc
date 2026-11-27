    function updateMealDatePresets(form){
      const selected=mealDatePreset({from:form.elements.from.value,to:form.elements.to.value});
      for(const button of form.querySelectorAll('[data-clear-meal-date]')){
        button.hidden=!form.elements[button.dataset.clearMealDate].value;
      }
      for(const button of form.querySelectorAll('[data-meal-date-preset]')){
        button.setAttribute('aria-pressed',String(button.dataset.mealDatePreset===selected));
      }
      form.querySelector('.ks-error').textContent='';
    }
    phone.addEventListener('click',event=>{
      if(event.target.matches('.ks-calculator-overlay')){closeCalculator(false);return;}
      if(event.target.matches('.ks-selection-overlay')){cancelSelection();return;}
      if(event.target.matches('[data-entry-category-overlay]')){closeEntryCategory();return;}
      if(event.target.matches('.ks-period-overlay')){closePeriod(false);return;}
      if(event.target.matches('.ks-calendar-overlay')){closeMealCalendar(false);return;}
      if(event.target.matches('.ks-time-overlay')){closeEntryTime(false);return;}
      if(event.target.matches('[data-meal-option-overlay]')){closeMealOption();return;}
      const b=event.target.closest('button');if(!b)return;
      capture();state.notice='';
      if(b.hasAttribute('data-select-close')){cancelSelection();return;}
      if(b.dataset.selectOpen==='meal-methods'){state.mealMethodPickerDraft=[...state.mealFilterDraft.methods];render();return;}
      if(b.dataset.selectOpen){state.singleSelection=b.dataset.selectOpen;render();return;}
      if(b.dataset.mealMethodAction){
        const action=b.dataset.mealMethodAction;
        if(action==='all'){state.mealMethodPickerDraft=mealFilterMethods().map(([value])=>value);render();}
        else if(action==='none'){state.mealMethodPickerDraft=[];render();}
        else if(action==='apply')closeMealMethodPicker(true);
        return;
      }
      if(b.dataset.selectValue!==undefined){closeSingleSelection(b.dataset.selectValue);return;}
      if(b.dataset.action==='add-entry-category'){state.singleSelection=null;state.addingEntryCategory=true;render();return;}
      if(b.dataset.action==='open-local-backup'){state.page='local-backup';render();return;}
      if(b.dataset.action==='open-csv-transfer'){state.page='csv-transfer';render();return;}
      if(b.dataset.localBackupAction){
        const action=b.dataset.localBackupAction,demo=state.localBackupDemo;
        if(action==='configure'){demo.scenario='normal';state.notice='备份目录已更新';}
        else if(action==='retry'){demo.scenario='normal';state.notice='自动备份检查通过';}
        else if(action==='manual'){state.notice='完整备份已导出并校验（示意）';}
        else if(action==='open-restore'){demo.restoreOpen=true;}
        else if(action==='close-restore'){demo.restoreOpen=false;}
        else if(action==='pick-candidate'||action==='pick-file'){demo.restoreOpen=false;state.notice='已进入完整恢复预览（示意）';}
        render();return;
      }
      if(b.dataset.csvKind){state.csvDemo.kind=b.dataset.csvKind;render();return;}
      if(b.dataset.csvAction){
        const action=b.dataset.csvAction,demo=state.csvDemo;
        if(action==='toggle-guide'){demo.guideOpen=!demo.guideOpen;}
        else if(action==='preview'){demo.previewOpen=true;}
        else if(action==='close-preview'){demo.previewOpen=false;}
        else if(action==='commit'){demo.previewOpen=false;state.notice=`CSV 导入已提交（新增 ${demo.additions} 条）`;}
        else if(action==='template'){state.notice=`${demo.kind==='MEALS'?'用餐':'记账'} CSV 模板已导出（示意）`;}
        else if(action==='export'){state.notice=`${demo.kind==='MEALS'?'用餐':'记账'} CSV 已导出（示意）`;}
        render();return;
      }
      if(b.dataset.action==='cancel-entry-category'){closeEntryCategory();return;}
      if(b.dataset.action==='open-entry-time'){
        const [hour,minute]=state.entryDraft.dateTime.slice(-5).split(':');
        state.entryTimeDraft={hour,minute,clock:false,part:'hour',clockTime:{hour,minute}};
        render();return;
      }
      if(b.closest('[data-entry-time]')){
        const d=state.entryTimeDraft;
        if(b.dataset.timeAction==='cancel'){closeEntryTime(false);return;}
        if(b.dataset.timeAction==='mode'){
          if(!d.clock){
            for(const part of ['hour','minute'])d[part]=d.clockTime[part]=validTimePart(d[part],part)?d[part]:d.clockTime[part];
            d.part='hour';
          }
          d.clock=!d.clock;
        }else if(b.dataset.timePart){
          d.part=b.dataset.timePart;
        }else if(b.dataset.timeValue!==undefined){
          d[d.part]=d.clockTime[d.part]=timeDigits(b.dataset.timeValue);
          d.part='minute';
        }else return;
        render();return;
      }
      if(b.dataset.action==='open-meal-calendar'){
        const compact=phone.clientWidth<360;
        state.mealCalendar={selected:state.date,month:state.date.slice(0,7),text:state.date,year:'',mode:compact?'text':'calendar',compact};
        render();return;
      }
      if(b.closest('.ks-calendar-dialog')){
        const d=state.mealCalendar,action=b.dataset.calendarAction;
        if(action==='cancel'||action==='apply'){closeMealCalendar(action==='apply');return;}
        let focus='[data-calendar-action=jump]';
        if(action==='jump'){
          d.mode=d.mode==='jump'?'calendar':'jump';d.year=String(Number(d.month.slice(0,4)));
        }else if(action==='mode'){
          if(d.mode==='text'){
            if(isDate(d.text)){d.selected=d.text;d.month=d.text.slice(0,7);}
            d.mode='calendar';
          }else{d.mode='text';d.text=d.selected;}
          focus='[data-calendar-action=mode]';
        }else if(b.dataset.calendarMonth){
          d.month=`${d.year.padStart(4,'0')}-${b.dataset.calendarMonth.padStart(2,'0')}`;d.mode='calendar';
        }else if(b.dataset.calendarStep){
          const month=new Date(`${d.month}-01T00:00:00Z`);month.setUTCMonth(month.getUTCMonth()+Number(b.dataset.calendarStep));
          d.month=month.toISOString().slice(0,7);
          focus=`[data-calendar-step="${b.dataset.calendarStep}"]:not(:disabled)`;
        }else if(b.dataset.calendarDay){
          d.selected=b.dataset.calendarDay;focus=`[data-calendar-day="${d.selected}"]`;
        }
        render();(phone.querySelector(focus)??phone.querySelector('[data-calendar-action=jump]'))?.focus({preventScroll:true});return;
      }
      if(b.dataset.githubAction){
        const action=b.dataset.githubAction,demo=state.githubDemo;
        if(action==='open'||action==='handle'){state.page='github';demo.review=null;}
        else if(action==='backup'||action==='apply-upload'){
          demo.uploading=true;demo.review=null;
          setTimeout(()=>{if(demo.uploading){demo.uploading=false;demo.scenario='normal';demo.backedUp=true;render();}},600);
        }
        else if(action==='apply-restore'){demo.scenario='restored';demo.review='notice';}
        else if(action==='configure'){demo.editing=true;demo.draftWifi=demo.wifi;}
        else if(action==='save-connection'){demo.wifi=demo.draftWifi;demo.editing=false;demo.scenario='review';}
        else if(action==='cancel-connection')demo.editing=false;
        else demo.review=action==='cancel'?null:action;
        render();
        if(!demo.review)phone.querySelector('[data-github-action=restore-preview]')?.focus({preventScroll:true});
        return;
      }
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
      if(b.dataset.action==='open-ledger-filters'){state.ledgerFilterDraft={...state.ledgerFilters};render();return;}
      if(b.dataset.filterBulk){
        const group=b.closest('[data-filter-group]'),key=group.dataset.filterGroup;
        const all=b.dataset.filterBulk==='all';
        state.ledgerFilterDraft[key]=all?null:[];
        group.querySelectorAll('input').forEach(input=>{input.checked=all;});
        updateLedgerFilterCount(group);
        return;
      }
      if(b.dataset.action==='choose-ledger-period'){
        const range=state.ledgerRange,[year,month]=state.ledgerMonth.split('-').map(n=>String(Number(n)));
        state.periodDraft={target:'ledger',mode:range?'range':'month',year,month,from:range?.from||'',to:range?.to||''};
        render();return;
      }
      if(b.dataset.action==='choose-meal-month'){
        const {from,to}=state.mealStatsFilters;
        const [year,month]=(from||to||demoToday).slice(0,7).split('-').map(n=>String(Number(n)));
        state.periodDraft={target:'meals',mode:'month',year,month};
        render();return;
      }
      if(b.dataset.action==='cancel-period'){closePeriod(false);return;}
      if(b.dataset.periodMode){
        state.periodDraft.mode=b.dataset.periodMode;render();
        phone.querySelector('[data-period-mode][aria-pressed=true]').focus({preventScroll:true});return;
      }
      if(b.dataset.periodStep){
        const d=state.periodDraft,ordinal=Number(d.year)*12+Number(d.month)-1+Number(b.dataset.periodStep);
        d.year=String(Math.floor(ordinal/12));
        d.month=String(ordinal%12+1);
        render();
        (phone.querySelector(`[data-period-step="${b.dataset.periodStep}"]:not(:disabled)`)??phone.querySelector('[name=year]')).focus({preventScroll:true});
        return;
      }
      if(b.dataset.periodMonth){
        state.periodDraft.month=b.dataset.periodMonth;render();
        phone.querySelector('[data-period-month][aria-pressed=true]').focus({preventScroll:true});return;
      }
      if(b.dataset.action==='meal-filter'){
        state.mealFilterDraft=state.mealTab==='统计'?state.mealStatsFilters:state.mealFilters;
        state.page='meal-filter';render();return;
      }
      if(b.dataset.mealDatePreset){
        const dates=mealDatePresets[b.dataset.mealDatePreset];
        b.form.elements.from.value=dates.from;
        b.form.elements.to.value=dates.to;
        updateMealDatePresets(b.form);
        return;
      }
      if(b.dataset.clearMealDate){
        const date=b.form.elements[b.dataset.clearMealDate];
        date.value='';
        updateMealDatePresets(b.form);
        date.focus({preventScroll:true});
        return;
      }
      if(b.dataset.action==='reset-meal-filter'){
        state.mealFilterDraft=blankMealFilters(state.mealTab==='统计'?mealDatePresets.month:mealDatePresets.all);
        render();return;
      }
      if(b.dataset.action==='meal-daily'){state.mealListMode=false;render();persist();return;}
      if(b.dataset.action==='clear-meal-filters'){state.mealFilters=blankMealFilters();render();persist();return;}
      if(b.dataset.action==='manage-categories'){state.categoryReturn=state.page;state.categoryWalletId=b.dataset.categoryWallet||'GLOBAL';state.financialScope='EXPENSE';state.editingFinancialCategoryId=null;state.page='category-manager';render();return;}
      if(b.dataset.financialScope){state.financialScope=b.dataset.financialScope;state.editingFinancialCategoryId=null;render();return;}
      if(b.dataset.editFinancialCategory){
        state.editingFinancialCategoryId=b.dataset.editFinancialCategory==='new'?null:b.dataset.editFinancialCategory;
        state.categoryDetailReturn=state.page;state.page='category-detail';render();return;
      }
      if(b.dataset.action==='edit-owned-categories'){state.page='category-owned';render();return;}
      if(b.dataset.action==='choose-wallets'){
        const scope=state.walletFilterIds;
        state.walletDraftMode=Array.isArray(scope)?'MANUAL':scope==='ALL'?'ALL':'REGULAR';
        state.walletDraftIds=Array.isArray(scope)?[...scope]:regularWallets().map(w=>w.id);
        state.walletPickerOpen=true;render();return;
      }
      if(b.dataset.editWallet){
        const wallet=walletById(b.dataset.editWallet);
        state.editingWalletId=wallet.id;state.page='wallet-edit';
        state.walletEditDraft={code:wallet.code,separateAccounting:wallet.separateAccounting,
          defaultToIncome:wallet.defaultToIncome,defaultWallet:state.defaultWalletId===wallet.id};
        render();return;
      }
      if(b.dataset.action==='save-wallet'){
        const wallet=walletById(state.editingWalletId),draft=state.walletEditDraft;
        wallet.separateAccounting=draft.separateAccounting;
        wallet.defaultToIncome=draft.defaultToIncome;
        if(wallet.code!==draft.code){
          wallet.code=draft.code;
          wallet.scale=new Intl.NumberFormat('zh-CN',{style:'currency',currency:wallet.code}).resolvedOptions().maximumFractionDigits;
        }
        if(!wallet.archived&&draft.defaultWallet)state.defaultWalletId=wallet.id;
        else if(state.defaultWalletId===wallet.id)state.defaultWalletId=null;
        state.page='ledger';render();persist();return;
      }
      if(b.dataset.walletMode){
        state.walletDraftMode=b.dataset.walletMode;render();
        phone.querySelector('[data-wallet-mode][aria-pressed=true]').focus({preventScroll:true});return;
      }
      if(b.dataset.walletAction==='default'){
        state.walletDraftMode='REGULAR';state.walletDraftIds=regularWallets().map(w=>w.id);render();
        phone.querySelector('[data-wallet-action=default]').focus({preventScroll:true});return;
      }
      if(b.dataset.walletAction){closeWalletPicker(b.dataset.walletAction);return;}
      if(b.dataset.entryId){if(!financialEntries().some(e=>e.id===b.dataset.entryId))return;recordOrigin={page:state.page,id:b.dataset.entryId};entrance=visible(b)?rect(b):null;state.selectedEntryId=b.dataset.entryId;state.page='entry-detail';render();return;}
      if(b.dataset.action==='delete-meal'){const meal=state.records[state.editingDate]?.[state.editingSlot];if(!meal)return;state.deletePending={kind:'meal',date:state.editingDate,slot:state.editingSlot,summary:`${state.editingDate} · ${slots[state.editingSlot]}\n${meal.dishes||'无菜品内容'}`};render();return;}
      if(b.dataset.action==='delete-entry'){const e=financialEntries().find(e=>e.id===state.selectedEntryId);if(!e)return;const w=walletById(e.walletId);state.deletePending={kind:'entry',id:e.id,summary:`${e.date} ${e.time}\n${w.name} · ${w.code}\n${entryCategory(e)} · ${e.type} · ${e.type==='支出'?'−':'+'}${money(e.cents,w.code)} ${w.code}`};render();return;}
      if(b.dataset.deleteAction){if(b.dataset.deleteAction==='confirm')confirmDelete();else cancelDelete();return;}
      if(b.dataset.action==='open-calculator'){openCalculator();return;}
      if(b.dataset.calcAction){closeCalculator(b.dataset.calcAction==='apply');return;}
      if(b.dataset.page){if(state.page===b.dataset.page)return;state.page=b.dataset.page;if(state.page==='settings')resetAppearance();render();persist();return;}
      if(b.dataset.date){
        state.date=b.dataset.date;render();
        if(b.dataset.weekStep)(phone.querySelector(`[data-week-step="${b.dataset.weekStep}"]:not(:disabled)`)||
          phone.querySelector('.ks-day[aria-pressed=true]'))?.focus({preventScroll:true});
        return;
      }
      if(b.dataset.meal!==undefined){if(state.page==='home')state.date='2026-11-17';else if(b.dataset.mealDate)state.date=b.dataset.mealDate;state.slot=Number(b.dataset.meal);state.mealReturn=state.page;const m=records()[state.slot];state.editing=!!m;if(m&&b.classList.contains('ks-record')){recordOrigin={page:state.page,date:state.date,slot:b.dataset.meal};entrance=visible(b)?rect(b):null;}else recordOrigin=null;state.editingDate=state.date;state.editingSlot=state.slot;state.atHome=m?m.atHome:true;state.method=m?m.method:'下厨';state.categories=[...(m?.categories||[])];state.dishes=m?.dishes||'';state.note=m?.note||'';state.page='meal-form';render();return;}
      if(b.dataset.homeChoice!==undefined){state.atHome=b.dataset.homeChoice==='true';render();return;}
      if(b.dataset.method){const m=b.dataset.method;state.method=state.method===m?null:m;render();return;}
      if(b.dataset.category){const c=b.dataset.category;state.categories=state.categories.includes(c)?state.categories.filter(x=>x!==c):[...state.categories,c];render();return;}
      if(b.dataset.mealTab){state.mealTab=b.dataset.mealTab;render();return;}
      if(b.dataset.ledgerTab){state.ledgerTab=b.dataset.ledgerTab;render();return;}
      if(b.dataset.entryType){
        state.followsWalletType=false;
        if(b.dataset.entryType!==state.entryType){
          state.entryType=b.dataset.entryType;
          state.entryDraft.category=chooseFinancialCategory(state.entryDraft.walletId,state.entryType,
            state.entryType==='收入'?'gi-salary':'ge-food');
        }
        render();return;
      }
      if(b.dataset.key!==undefined){
        if(b.dataset.key!=='⌫'||!calcBackspaceRepeated||event.detail===0)pressCalculatorKey(b.dataset.key);
        return;
      }
      if(b.dataset.action==='add-entry'){
        const wallet=defaultEntryWallet(),walletId=wallet?.id||'';
        state.entryType=defaultEntryType(wallet);
        state.followsWalletType=true;
        const category=(state.entryType==='支出'?chooseFinancialCategory(walletId,'支出','ge-food'):'')
          ||initialEntryCategory(walletId,state.entryType);
        state.entryReturn=state.page;state.page='entry';state.amount='';
        state.entryDraft={dateTime:`${demoToday}T09:41`,note:'',category,walletId};
        render();
        const draft=state.entryDraft;
        if(wallet)Promise.allSettled(pageNode().getAnimations().map(animation=>animation.finished)).then(()=>{
          if(state.page==='entry'&&state.entryDraft===draft)openCalculator();
        });
        return;
      }
      if(b.dataset.action==='back'){state.page=state.page==='entry'?state.entryReturn:state.page==='category-detail'?state.categoryDetailReturn:state.page==='category-owned'?'category-manager':state.page==='category-manager'?state.categoryReturn:['appearance','github','local-backup','csv-transfer'].includes(state.page)?'settings':state.page==='meal-filter'?'meals':state.page==='meal-form'?state.mealReturn:'ledger';render();return;}
      if(b.dataset.addMealOption){state.addingMealOption=b.dataset.addMealOption;render();return;}
      if(b.dataset.action==='cancel-meal-option'){closeMealOption();return;}
    });
    let calcBackspacePointer=null,calcBackspaceTimer,calcBackspaceRepeated=false;
    function stopCalcBackspace(){
      clearTimeout(calcBackspaceTimer);clearInterval(calcBackspaceTimer);
      calcBackspacePointer=null;
    }
    phone.addEventListener('pointerdown',event=>{
      const key=event.target.closest('[data-key="⌫"]');
      if(!key||!event.isPrimary||event.button!==0)return;
      stopCalcBackspace();
      calcBackspacePointer=event.pointerId;calcBackspaceRepeated=false;key.setPointerCapture(event.pointerId);
      calcBackspaceTimer=setTimeout(()=>{
        calcBackspaceRepeated=true;
        pressCalculatorKey('⌫');
        calcBackspaceTimer=setInterval(()=>pressCalculatorKey('⌫'),75);
      },500);
    });
    for(const type of ['pointerup','pointercancel','lostpointercapture']){
      phone.addEventListener(type,event=>{if(event.pointerId===calcBackspacePointer)stopCalcBackspace();});
    }
    window.addEventListener('blur',stopCalcBackspace);
    phone.addEventListener('contextmenu',event=>{
      if(event.target.closest('[data-key="⌫"]'))event.preventDefault();
    });
    let selectionDrag=null;
    const resetSelectionDrag=()=>{
      if(!selectionDrag)return;
      const {panel,dragging}=selectionDrag;
      selectionDrag=null;
      if(!panel||!dragging)return;
      panel.style.transition=reducedMotion.matches?'':`transform 200ms ${motion.easing}`;
      panel.style.transform='';
      setTimeout(()=>{if(panel.isConnected)panel.style.transition='';},220);
    };
    phone.addEventListener('pointerdown',event=>{
      const header=event.target.closest('.ks-selection-handle,.ks-selection-heading');
      if(!header||!event.isPrimary||event.target.closest('.ks-selection-close'))return;
      const panel=header.closest('.ks-selection-panel');
      if(!panel)return;
      selectionDrag={id:event.pointerId,x:event.clientX,y:event.clientY,lastY:event.clientY,lastT:event.timeStamp,lastMoveT:event.timeStamp,vy:0,
        dragging:false,target:header,panel,calculator:panel.classList.contains('ks-calc-sheet')};
    });
    phone.addEventListener('pointermove',event=>{
      if(selectionDrag?.id!==event.pointerId)return;
      const dy=event.clientY-selectionDrag.y,dx=event.clientX-selectionDrag.x;
      if(!selectionDrag.dragging){
        if(Math.abs(dy)<=8||Math.abs(dy)<=Math.abs(dx))return;
        selectionDrag.dragging=true;
        try{selectionDrag.target.setPointerCapture(event.pointerId);}catch{}
        selectionDrag.panel.style.transition='none';
      }
      const dt=event.timeStamp-selectionDrag.lastT,stepY=event.clientY-selectionDrag.lastY;
      if(dt>0)selectionDrag.vy=stepY/(dt/1000);
      if(Math.abs(stepY)>=1)selectionDrag.lastMoveT=event.timeStamp;
      selectionDrag.lastY=event.clientY;selectionDrag.lastT=event.timeStamp;
      selectionDrag.panel.style.transform=`translateY(${Math.max(0,dy)}px)`;
    });
    phone.addEventListener('pointerup',event=>{
      if(selectionDrag?.id!==event.pointerId)return;
      const dy=Math.max(0,event.clientY-selectionDrag.y);
      const {calculator,dragging,lastMoveT,panel,vy}=selectionDrag;
      const effectiveVy=event.timeStamp-lastMoveT>=100?0:vy;
      const dismiss=dragging&&(dy>=100||(dy>=24&&effectiveVy>=800));
      if(dismiss){
        selectionDrag=null;panel.style.transition='';panel.style.transform='';
        if(calculator)closeCalculator(false);else cancelSelection();
      }else resetSelectionDrag();
    });
    phone.addEventListener('pointercancel',resetSelectionDrag);
    phone.addEventListener('keydown',event=>{
      if(!modalKind())return;
      if(event.key==='Escape'){
        event.preventDefault();
        if(state.githubDemo.review){
          state.githubDemo.review=null;render();phone.querySelector('[data-github-action=restore-preview]')?.focus({preventScroll:true});
        }else if(state.localBackupDemo.restoreOpen){state.localBackupDemo.restoreOpen=false;render();}
        else if(state.csvDemo.previewOpen&&!state.singleSelection){state.csvDemo.previewOpen=false;render();}
        else if(state.addingMealOption)closeMealOption();
        else if(state.mealCalendar)closeMealCalendar(false);
        else if(state.entryTimeDraft)closeEntryTime(false);
        else if(state.addingEntryCategory)closeEntryCategory();
        else if(state.periodDraft)closePeriod(false);
        else if(state.singleSelection||state.mealMethodPickerDraft||state.ledgerFilterDraft||state.walletPickerOpen)cancelSelection();
        else if(state.deletePending)cancelDelete();
        else closeCalculator(false);
      }else if(event.key==='Enter'&&event.target.matches('[data-ledger-filters] [name=note]')){
        event.preventDefault();
      }else if(event.key==='Enter'&&event.target.matches('[data-entry-time] [name=hour]')){
        event.preventDefault();event.target.form.elements.minute.focus();
      }else if(event.key==='Tab'){
        const dialog=modalNode().firstElementChild,controls=[...dialog.querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),[tabindex="0"]')],first=controls[0],last=controls[controls.length-1];
        if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}
        else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
      }
    });
    phone.addEventListener('focusin',event=>{
      if(event.target.matches('[data-entry-time] input'))event.target.select();
    });
    phone.addEventListener('change',event=>{
      const e=event.target;
      if(e.matches('[data-filter-group] input[type=checkbox]')){
        const group=e.closest('[data-filter-group]'),key=group.dataset.filterGroup;
        const options=[...group.querySelectorAll('input')].map(input=>input.value);
        const selected=new Set(state.ledgerFilterDraft[key]??options);
        if(e.checked)selected.add(e.value);else selected.delete(e.value);
        state.ledgerFilterDraft[key]=selected.size===options.length&&options.every(value=>selected.has(value))
          ?null:[...selected];
        updateLedgerFilterCount(group);
        return;
      }
      if(e.matches('[data-meal-method-option]')){
        const val=e.dataset.mealMethodOption,scroll=e.closest('.ks-selection-body').scrollTop;
        const current=state.mealMethodPickerDraft||[];
        state.mealMethodPickerDraft=e.checked?[...new Set([...current,val])]:current.filter(x=>x!==val);
        render();phone.querySelector('.ks-selection-body').scrollTop=scroll;
        phone.querySelector(`[data-meal-method-option="${CSS.escape(val)}"]`)?.focus({preventScroll:true});return;
      }
      if(e.matches('[data-github-wifi]')){state.githubDemo.wifi=e.checked;return;}
      if(e.matches('[data-github-wifi-draft]')){state.githubDemo.draftWifi=e.checked;return;}
      if(e.matches('[data-github-scenario]')){
        state.githubDemo.scenario=e.value;state.githubDemo.editing=false;state.githubDemo.uploading=false;
        state.githubDemo.review=githubPause()?'notice':null;render();return;
      }
      if(e.matches('[data-local-backup-scenario]')){
        state.localBackupDemo.scenario=e.value;state.localBackupDemo.restoreOpen=false;render();return;
      }
      if(e.matches('[data-wallet-option]')){
        const id=e.dataset.walletOption,scroll=e.closest('.ks-selection-body').scrollTop;
        state.walletDraftIds=e.checked?[...new Set([...state.walletDraftIds,id])]:state.walletDraftIds.filter(x=>x!==id);
        render();phone.querySelector('.ks-selection-body').scrollTop=scroll;
        phone.querySelector(`[data-wallet-option=${id}]`).focus({preventScroll:true});return;
      }
      if(e.matches('[data-meal-filter-form] input[type=date]')){updateMealDatePresets(e.form);return;}
    });
    phone.addEventListener('input',event=>{
      const e=event.target;
      if(e.matches('[data-currency-search]')){
        const body=e.closest('.ks-selection-panel').querySelector('.ks-selection-body');
        body.innerHTML=currencyOptions(e.value);
        body.scrollTop=0;
        globalThis.lucide?.createIcons({root:body,attrs:{width:18,height:18}});
        return;
      }
      if(e.matches('[data-entry-time] input')){
        const d=state.entryTimeDraft;
        e.value=e.value.replace(/\D/g,'');
        d[e.name]=e.value;
        const valid=validTimePart(e.value,e.name);
        e.setAttribute('aria-invalid',String(e.value.length===2&&!valid));
        e.form.querySelector('[type=submit]').disabled=!validTimePart(d.hour,'hour')||!validTimePart(d.minute,'minute');
        if(e.name==='hour'&&valid)e.form.elements.minute.focus();
        return;
      }
      if(e.matches('[data-meal-option-form] input')){
        e.form.querySelector('[type=submit]').disabled=!e.value.trim();
        e.form.querySelector('[data-meal-option-error]').textContent='';
        return;
      }
      if(e.matches('[data-calendar-year],[data-calendar-text]')){
        const d=state.mealCalendar,dialog=e.closest('.ks-calendar-dialog');
        if(e.matches('[data-calendar-year]')){
          d.year=e.value;const valid=validCalendarYear(d.year),[year,month]=d.month.split('-').map(Number);
          for(const b of dialog.querySelectorAll('[data-calendar-month]')){
            b.disabled=!valid;b.setAttribute('aria-pressed',valid&&Number(d.year)===year&&Number(b.dataset.calendarMonth)===month);
          }
          dialog.querySelector('[data-calendar-error]').textContent=valid?'':'请输入 1 至 9999 的年份';
        }else{
          d.text=e.value;const valid=isDate(d.text);
          dialog.querySelector('[data-calendar-action=apply]').disabled=!valid;
          dialog.querySelector('[data-calendar-error]').textContent=d.text&&!valid?'请输入有效日期':'';
        }
        return;
      }
      if(e.matches('[data-period-form] input')){
        const draft=state.periodDraft,form=e.form;
        draft[e.name]=e.value;
        form.querySelector('[data-period-error]').textContent=periodError(draft);
        const valid=canApplyPeriod(draft);
        form.querySelector('[type=submit]').disabled=!valid;
        if(e.name==='year'){
          const ordinal=Number(draft.year)*12+Number(draft.month)-1;
          form.querySelector('[data-period-step="-1"]').disabled=!valid||ordinal<=12;
          form.querySelector('[data-period-step="1"]').disabled=!valid||ordinal>=119999;
          form.querySelector('[data-period-month-label]').textContent=valid?`${Number(draft.year)}年${draft.month}月`:'';
        }
        return;
      }
      if(!e.matches('[data-custom-picker],[data-custom-hex]'))return;
      state.appearanceDraft.customColor=e.value;
      const normalized=normalizeHex(e.value);
      if(e.matches('[data-custom-picker]'))phone.querySelector('[data-custom-hex]').value=normalized;
      if(normalized){phone.querySelector('[data-custom-picker]').value=normalized;phone.querySelector('[data-appearance-error]').textContent='';}
      updateAppearancePreview();
    });
    phone.addEventListener('submit',event=>{
      event.preventDefault();const f=event.target;capture();
      if(f.matches('[data-entry-time]')){closeEntryTime(true);return;}
      if(f.matches('[data-meal-option-form]')){
        const name=f.elements.optionName.value.trim(),method=state.addingMealOption==='method';
        const list=method?mealMethods:mealPreparations,archived=method?archivedMealMethods:archivedMealPreparations;
        const normalize=text=>text.normalize('NFC').toLowerCase();
        const message=!name?'请填写名称':[...list,...archived].some(item=>normalize(item)===normalize(name))
          ?method?'已有同名用餐方式':'此范围已有同名分类':'';
        if(message){f.querySelector('[data-meal-option-error]').textContent=message;return;}
        list.push(name);closeMealOption();return;
      }
      if(f.matches('[data-financial-category-form],[data-entry-category]')){
        const fromEntry=f.matches('[data-entry-category]'),wallet=entryWallet();
        if(fromEntry&&!wallet){closeEntryCategory();return;}
        const scope=fromEntry?financialScopeOf(state.entryType):state.financialScope;
        const owner=fromEntry?(scopeMode(wallet,scope)==='CUSTOM'?wallet.id:'GLOBAL'):state.categoryWalletId;
        const items=owner==='GLOBAL'?sharedFinancialCategories[scope]:walletFinancialCategories[owner][scope];
        const edited=fromEntry?null:items.find(c=>c.id===state.editingFinancialCategoryId);
        const name=f.elements.categoryName.value.trim(),normalized=categoryNameKey({name});
        if(!name||normalized==='未分类'||items.some(c=>c.id!==edited?.id&&categoryNameKey(c)===normalized)){
          f.querySelector('.ks-error').textContent='分类名不能为空、不能使用“未分类”，且不能与本范围已有名称重复。';
          return;
        }
        const icon=f.elements.categoryIcon.value;
        const archived=fromEntry?false:f.elements.archived.checked;
        const sortOrder=fromEntry?nextCategoryOrder(items):f.elements.sortOrder.valueAsNumber;
        const category=edited??{id:'user-cat-'+state.nextFinancialCategoryId++};
        Object.assign(category,{name,icon,archived,sortOrder});
        if(!edited)items.push(category);
        items.sort((a,b)=>a.sortOrder-b.sortOrder||a.name.localeCompare(b.name));
        if(fromEntry){
          state.entryDraft.category=category.id;
          state.followsWalletType=false;
          closeEntryCategory();return;
        }
        state.editingFinancialCategoryId=null;state.page=state.categoryDetailReturn;render();return;
      }
      if(f.matches('[data-ledger-filters]')){closeLedgerFilters(true);return;}
      if(f.matches('[data-period-form]')){closePeriod(true);return;}
      if(f.matches('[data-meal-filter-form]')){
        const {from,to,keyword}=f.elements;
        if(from.value&&to.value&&from.value>to.value){error('开始日期不能晚于结束日期。');return;}
        const pickedSlots=[...f.querySelectorAll('[name=slot]:checked')].map(e=>e.value);
        const pickedHome=[...f.querySelectorAll('[name=atHome]:checked')].map(e=>e.value);
        const allMethods=mealFilterMethods().map(([value])=>value);
        const pickedMethods=allMethods.filter(v=>state.mealFilterDraft.methods.includes(v));
        const applied={
          from:from.value,to:to.value,
          slots:pickedSlots.length===slots.length?[]:pickedSlots,
          atHome:pickedHome.length===2?[]:pickedHome,
          methods:pickedMethods.length===allMethods.length?[]:pickedMethods,
          preparations:[...f.querySelectorAll('[name=preparation]:checked')].map(e=>e.value),keyword:keyword.value.trim(),
        };
        if(state.mealTab==='统计')state.mealStatsFilters=applied;
        else{state.mealFilters=applied;state.mealListMode=true;}
        state.page='meals';render();persist();return;
      }
      if(f.matches('[data-meal-form]')){
        if(state.atHome===null){error('请先选择是否计划在家吃。');return;}
        if(state.atHome&&!state.method){error('计划在家吃时，请选择一种用餐方式。');return;}
        const list=records();if(list[state.slot]&&!(state.editing&&state.date===state.editingDate&&state.slot===state.editingSlot)){error('当天这一餐已有记录，请返回后编辑已有记录。');return;}
        // The final method decides categories: switching away and back keeps them; another method without categories clears them.
        const original=state.editing?state.records[state.editingDate]?.[state.editingSlot]:null;
        const categories=state.method==='下厨'?[...state.categories]:state.method&&state.method===original?.method?[...(original.categories||[])]:[];
        if(state.editing&&(state.date!==state.editingDate||state.slot!==state.editingSlot))state.records[state.editingDate][state.editingSlot]=null;
        state.records[state.date]||=[null,null,null];state.records[state.date][state.slot]={atHome:state.atHome,method:state.method,categories,dishes:state.dishes,note:state.note};state.page=state.mealReturn;state.notice='已保存到当前演示。';render();return;
      }
      if(f.matches('[data-entry-form]')){
        try{
          const {walletId,category}=state.entryDraft;
          if(!walletId)throw Error('请先选择钱包。');
          const cents=calcCents(state.amount);
          if(!effectiveFinancialCategories(walletId,state.entryType).some(c=>c.id===category))throw Error('请选择当前钱包可用的分类。');
          const entry={id:`draft-${state.nextEntryId++}`,type:state.entryType,cents,walletId,category,note:f.elements.note.value,date:f.elements.date.value,time:state.entryDraft.dateTime.slice(-5)};
          state.entries.push(entry);if(!walletById(walletId).separateAccounting)state.lastWalletId=walletId;state.page=state.entryReturn;
          state.notice=state.page==='ledger'&&!matchesLedgerScope(entry)?'已保存到当前演示，该账目不在当前筛选范围内。':'已保存到当前演示。';
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

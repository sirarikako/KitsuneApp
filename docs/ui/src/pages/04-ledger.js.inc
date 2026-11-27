    function ledgerCategoryPanel(type,code,entries){
      const income=type==='收入',totals=new Map();
      for(const entry of entries){
        if(entry.type!==type&&(income||entry.type!=='退款'))continue;
        const category=categoryForEntry(entry),amount=entry.type==='退款'?-entry.cents:entry.cents;
        totals.set(category,(totals.get(category)??0n)+amount);
      }
      if(!totals.size)return '';
      const abs=amount=>amount<0n?-amount:amount;
      const rows=[...totals].map(([id,amount])=>({name:categoryDescriptionForId(id),amount}))
        .sort((a,b)=>a.amount===b.amount?a.name.localeCompare(b.name,'zh-CN'):a.amount>b.amount?-1:1);
      const magnitude=rows.reduce((sum,row)=>sum+abs(row.amount),0n);
      const title=`${income?'收入':'净支出'}分类 · ${code}`;
      const note=rows.at(-1).amount<0n?'条长按绝对值占比':'金额占比';
      return `${section(title,magnitude>0n?`<span class="ks-note">${note}</span>`:'')}
        <div class="ks-stats-panel">${rows.map(({name,amount})=>`<div class="ks-stat-row">
          <div class="ks-row"><span>${escapeText(name)}</span><strong>${money(amount,code)}</strong></div>
          ${amount!==0n?statBar(name+'金额占比',Number(abs(amount))/Number(magnitude),income?'ks-bar-income':'ks-bar-expense'):''}
        </div>`).join('')}</div>`;
    }
    const ledgerTabs=()=>`<div class="ks-segments" aria-label="记账视图">${['流水','钱包','统计'].map(tab=>`<button type="button" data-ledger-tab="${tab}" aria-pressed="${state.ledgerTab===tab}" class="cursor-interaction">${tab}</button>`).join('')}</div>`;
    function ledgerFilters(){
      if(state.ledgerTab==='钱包')return '';
      const empty=!selectedWallets().length&&wallets.length;
      return `<div class="ks-ledger-collapsible"><div class="ks-ledger-filters">
        <div class="ks-ledger-filter-row ${state.ledgerRange?'ks-ledger-custom':''}">${ledgerPeriodControl()}${walletScope()}</div>
        ${empty?'<span class="ks-note">当前范围没有钱包，可手动选择</span>':''}
      </div></div>`;
    }
    function attachLedgerScroll(position, collapsed, compact){
      const main=phone.querySelector('.ks-ledger-screen .ks-main');
      if(!main)return;
      const parts=[...phone.querySelectorAll('.ks-ledger-screen .ks-ledger-collapsible')];
      const fab=phone.querySelector('.ks-ledger-fab');
      let sizes=[],height=0,previous=position;
      const layout=()=>{
        const visible=height?1-collapsed/height:1;
        parts.forEach((part,i)=>{
          part.style.height=`${sizes[i]*visible}px`;
          part.inert=visible===0;
        });
        fab?.classList.toggle('ks-fab-compact',compact);
      };
      const measure=()=>{
        sizes=parts.map(part=>part.firstElementChild.offsetHeight);
        height=sizes.reduce((sum,size)=>sum+size,0);
        collapsed=Math.min(height,collapsed);
        layout();
      };
      measure();
      main.scrollTop=position;
      previous=main.scrollTop;
      main.addEventListener('scroll',()=>{
        const current=main.scrollTop,delta=current-previous;
        if(!delta)return;
        collapsed=current<=0?0:Math.min(height,Math.max(0,collapsed+delta));
        compact=current>0;
        layout();
        // A taller viewport may clamp the scroll offset at the end.
        previous=main.scrollTop;
      },{passive:true});
      const observer=new ResizeObserver(()=>{
        if(!main.isConnected){observer.disconnect();return;}
        measure();
        previous=main.scrollTop;
      });
      parts.forEach(part=>observer.observe(part.firstElementChild));
      if(fab){
        let drag=null,suppressClick=false;
        fab.addEventListener('wheel',event=>{
          if(!event.deltaY)return;
          event.preventDefault();
          main.scrollTop+=event.deltaY;
        },{passive:false});
        fab.addEventListener('pointerdown',event=>{
          if(!event.isPrimary||(event.pointerType==='mouse'&&event.button!==0))return;
          drag={id:event.pointerId,x:event.clientX,y:event.clientY,lastY:event.clientY,dragging:false};
        });
        fab.addEventListener('pointermove',event=>{
          if(drag?.id!==event.pointerId)return;
          const dy=event.clientY-drag.y,dx=event.clientX-drag.x;
          if(!drag.dragging){
            if(Math.abs(dy)<=8||Math.abs(dy)<Math.abs(dx))return;
            drag.dragging=true;suppressClick=true;
            try{fab.setPointerCapture(event.pointerId);}catch{}
            main.scrollTop-=dy;
          }else{
            main.scrollTop-=event.clientY-drag.lastY;
          }
          drag.lastY=event.clientY;
        });
        const finishDrag=event=>{
          if(drag?.id!==event.pointerId)return;
          const wasDragging=drag.dragging;
          drag=null;
          if(wasDragging)setTimeout(()=>{suppressClick=false;},0);
        };
        fab.addEventListener('pointerup',finishDrag);
        fab.addEventListener('pointercancel',finishDrag);
        fab.addEventListener('click',event=>{
          if(!suppressClick)return;
          suppressClick=false;
          event.preventDefault();
          event.stopPropagation();
        });
      }
    }
    function ledgerFiltersDialog(){
      const draft=state.ledgerFilterDraft;
      const group=(label,key,options)=>{
        const types=key==='types';
        const checked=id=>draft[key]===null||draft[key].includes(id);
        const count=options.filter(([id])=>checked(id)).length;
        return `<div class="${types?'':'ks-selection-section'}" data-filter-group="${key}" role="group" aria-labelledby="ledger-filter-${key}">
          <div class="ks-selection-group-heading">
            <div class="ks-selection-group-name"><h4 id="ledger-filter-${key}">${label}</h4>
              ${types?'':`<span class="ks-note" data-filter-count>${count}/${options.length}</span>`}</div>
            <button type="button" class="ks-text-btn" data-filter-bulk="all" aria-label="全选${label}">全选</button>
            <button type="button" class="ks-text-btn" data-filter-bulk="none" aria-label="全不选${label}">全不选</button>
          </div>
          ${types?`<div class="ks-selection-chips">${options.map(([id,text])=>`<label class="ks-selection-chip">
            <input type="checkbox" value="${escapeText(id)}" ${checked(id)?'checked':''}>
            <span aria-hidden="true">${icon('check')}</span>${escapeText(text)}</label>`).join('')}</div>`:
            `<div class="ks-selection-checks">${options.map(([id,text,glyph])=>selectionCheck(text,`value="${escapeText(id)}"`,checked(id),'',glyph)).join('')}
              ${options.length?'':'<p class="ks-note">当前钱包范围没有可选分类</p>'}</div>`}
        </div>`;
      };
      return selectionPanel('流水筛选',
        group('类型','types',['收入','支出','转账','退款'].map(value=>[value,value]))+
        group('分类','categoryNames',ledgerFilterCategories())+
        `<label class="ks-selection-note"><span class="ks-label">备注关键词</span><input name="note" class="ks-control" value="${escapeText(draft.note)}"></label>`,
        `<button type="submit" class="ks-primary">应用</button>`,
        {attrs:'data-ledger-filters',form:true});
    }
    function updateLedgerFilterCount(group){
      const count=group.querySelector('[data-filter-count]');
      if(count)count.textContent=`${group.querySelectorAll('input:checked').length}/${group.querySelectorAll('input').length}`;
    }
    function pageLedger(){
      const allEntries=financialEntries();
      if(state.ledgerTab==='钱包')return `${section('我的钱包','<span class="ks-note">每个钱包余额单独计算</span>')}<div class="ks-wallet-cards">
        ${wallets.map(wallet=>{
          const related=allEntries.filter(entry=>entry.walletId===wallet.id);
          const balance=wallet.opening+total(related,'收入')-netExpense(related);
          const label=escapeText(wallet.name+' · '+wallet.code);
          return `<div class="ks-money-card" data-wallet-balance="${wallet.id}">
            <div class="ks-wallet-card-heading">
              <div class="ks-money-caption ks-wallet-card-label"><span>${label}</span>${state.defaultWalletId===wallet.id?'<span class="ks-pill">默认钱包</span>':''}${wallet.separateAccounting?'<span class="ks-pill">独立核算</span>':''}${wallet.archived?'<span class="ks-pill">已归档</span>':''}</div>
              <div class="ks-wallet-card-actions">
                <button type="button" class="ks-icon-btn cursor-interaction" data-action="manage-categories" data-category-wallet="${wallet.id}" title="管理本钱包分类" aria-label="管理本钱包分类：${label}">${icon('tags')}</button>
                <button type="button" class="ks-icon-btn cursor-interaction" data-edit-wallet="${wallet.id}" title="编辑钱包" aria-label="编辑钱包：${label}">${icon('pencil')}</button>
              </div>
            </div>
            <div class="ks-amount">${money(balance,wallet.code)}</div>
            <div class="ks-note">初始余额 ${money(wallet.opening,wallet.code)} · 全部流水</div>
          </div>`;
        }).join('')}</div><p class="ks-footnote">交互预览 · 示例数据</p>`;
      const entries=allEntries.filter(matchesLedgerScope).sort((a,b)=>(b.date+b.time).localeCompare(a.date+a.time));
      const selected=selectedWallets(),groups=currencyGroups(entries,selected);
      const period=state.ledgerRange?'所选期间':'本月';
      const includeYear=state.ledgerRange&&state.ledgerRange.from.slice(0,4)!==state.ledgerRange.to.slice(0,4);
      const empty='<p class="ks-empty-state">所选日期和钱包范围内没有账目</p>';
      if(state.ledgerTab==='统计')return `<div data-ledger-statistics aria-live="polite">
        ${groups.map(({code,entries:group})=>`<section class="ks-currency-group" data-currency="${code}">
            ${section(`${period}收支 · ${code}`)}
            <div class="ks-summary">
              <div><span class="ks-note">收入 · ${code}</span><strong data-income>${money(total(group,'收入'),code)}</strong></div>
              <div><span class="ks-note">净支出 · ${code}</span><strong data-expense>${money(netExpense(group),code)}</strong></div>
            </div>
            ${ledgerCategoryPanel('收入',code,group)}
            ${ledgerCategoryPanel('支出',code,group)}
            ${group.length?'':empty}
          </section>`).join('')}
      </div><p class="ks-footnote">交互预览 · 各币种独立统计</p>`;
      const {types,categoryNames,note}=state.ledgerFilters;
      const keyword=note.trim().toLocaleLowerCase();
      const visibleEntries=entries.filter(entry=>(types===null||types.includes(entry.type))&&
        (categoryNames===null||categoryNames.includes(categoryNameKey(categoryById(categoryForEntry(entry)))))&&
        entry.note.toLocaleLowerCase().includes(keyword));
      const activeFilters=types!==null||categoryNames!==null||Boolean(keyword);
      const filters=`<div class="ks-ledger-list-actions"><span class="ks-note">${visibleEntries.length} 笔</span>
        <button type="button" class="ks-choice ks-date" data-action="open-ledger-filters" aria-haspopup="dialog" aria-pressed="${activeFilters}">${icon('list-filter')}筛选</button></div>`;
      return `<section data-monthly-ledger aria-label="所选日期与钱包的流水" aria-live="polite">
        ${groups.map(({code,entries:group})=>`<div class="ks-currency-group">${moneyCard(code,group,selected.filter(w=>w.code===code).every(w=>w.defaultToIncome))}</div>`).join('')}
        ${section(`${period}流水`,filters)}
        <div class="ks-record-list">
        ${visibleEntries.map(entry=>{
          const wallet=walletById(entry.walletId),category=categoryById(categoryForEntry(entry));
          const categoryName=category?.name||'历史分类';
          const lineLabel=entry.type==='退款'?`退款 · ${categoryName}`:categoryName;
          return `<button type="button" class="ks-record cursor-interaction" data-entry-id="${entry.id}" aria-label="查看${escapeText(wallet.name)} ${escapeText(categoryName)} ${entry.type} ${wallet.code} ${money(entry.cents,wallet.code)}详情">
            ${recordIcon(categoryIconName(category?.icon))}
            <span class="ks-record-copy"><span class="ks-record-title">${escapeText(lineLabel)}<span class="ks-pill">${escapeText(wallet.name)}</span></span><span class="ks-record-meta ks-record-ellipsis">${includeYear?entry.date:entry.date.slice(5)} ${entry.time}${entry.note?` · ${escapeText(entry.note)}`:''}</span></span>
            <span class="ks-currency-amount ${entry.type==='支出'?'ks-expense':'ks-income'}"><strong>${entry.type==='支出'?'−':'+'}${money(entry.cents,wallet.code)}</strong><small>${wallet.code}</small></span>${icon('chevron-right')}
          </button>`;
        }).join('')}
        </div>
        ${visibleEntries.length?'':empty}
      </section><p class="ks-footnote">交互预览 · 示例数据</p>`;
    }
    function pageWalletEdit(){
      const wallet=walletById(state.editingWalletId),draft=state.walletEditDraft;
      const locked=financialEntries().some(entry=>entry.walletId===wallet.id||entry.toWalletId===wallet.id);
      return `<section class="ks-money-card ks-form">
        <div class="ks-money-caption">${escapeText(wallet.name)}</div>
        ${selectionControl('currency','币种',draft.code,'',locked)}
        ${locked?'<span class="ks-note">已有流水，币种和精度固定</span>':''}
        <label class="ks-wallet-setting"><span>独立核算<small>开启后默认排除本钱包的收支和流水。余额与历史记录保留，可在账本手动选择。</small></span><input type="checkbox" data-wallet-independent ${draft.separateAccounting?'checked':''}></label>
        <label class="ks-wallet-setting"><span>设为默认钱包</span><input type="checkbox" data-wallet-default ${draft.defaultWallet?'checked':''} ${wallet.archived?'disabled':''}></label>
        <label class="ks-wallet-setting"><span>默认记为收入</span><input type="checkbox" data-wallet-income-default ${draft.defaultToIncome?'checked':''}></label>
        <button type="button" class="ks-primary ks-full cursor-interaction" data-action="save-wallet">保存</button>
      </section><p class="ks-footnote">交互预览 · 示例数据</p>`;
    }

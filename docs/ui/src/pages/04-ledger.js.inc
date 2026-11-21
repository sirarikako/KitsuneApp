    function pageLedger(){
      const allEntries=financialEntries();
      const tabs=`<div class="ks-segments" aria-label="记账视图">${['流水','钱包','统计'].map(tab=>`<button type="button" data-ledger-tab="${tab}" aria-pressed="${state.ledgerTab===tab}" class="cursor-interaction">${tab}</button>`).join('')}</div>`;
      if(state.ledgerTab==='钱包')return `${tabs}${section('我的钱包','<span class="ks-note">每个钱包余额单独计算</span>')}
        ${wallets.map(wallet=>{
          const related=allEntries.filter(entry=>entry.walletId===wallet.id);
          const balance=wallet.opening+total(related,'收入')-netExpense(related);
          return `<div class="ks-currency-group"><div class="ks-money-card" data-wallet-balance="${wallet.id}">
            <span class="ks-money-caption">${wallet.name} · ${wallet.code} ${wallet.separateAccounting?'<span class="ks-pill">独立核算</span>':''}</span>
            <div class="ks-amount">${money(balance,wallet.code)}</div>
            <div class="ks-note">初始余额 ${money(wallet.opening,wallet.code)} · 全部流水</div><label class="ks-wallet-setting"><span>独立核算<small>默认不参与收支统计与流水</small></span><input type="checkbox" data-wallet-independent="${wallet.id}" aria-label="${wallet.name}独立核算" ${wallet.separateAccounting?'checked':''}></label><button type="button" class="ks-choice ks-category-shortcut cursor-interaction" data-action="manage-categories" data-category-wallet="${wallet.id}">${icon('tags')}管理本钱包分类</button>
          </div></div>`;
        }).join('')}<p class="ks-footnote">交互预览 · 示例数据</p>`;
      const entries=allEntries.filter(matchesLedgerScope).sort((a,b)=>(b.date+b.time).localeCompare(a.date+a.time));
      const picker=ledgerDatePicker()+walletScope(),groups=currencyGroups(entries,selectedWallets());
      const period=state.ledgerRange?'所选期间':'本月';
      const includeYear=state.ledgerRange&&state.ledgerRange.from.slice(0,4)!==state.ledgerRange.to.slice(0,4);
      const empty='<p class="ks-empty-state">所选日期和钱包范围内没有账目</p>';
      if(state.ledgerTab==='统计')return `${tabs}${picker}<div data-ledger-statistics aria-live="polite">
        ${groups.map(({code,entries:group})=>{
          const expenses=group.filter(entry=>entry.type==='支出'||entry.type==='退款');
          const categories=[...new Set(expenses.map(categoryForEntry))]
            .map(id=>({id,name:categoryDescriptionForId(id),amount:netExpense(expenses.filter(entry=>categoryForEntry(entry)===id))}))
            .sort((a,b)=>a.amount===b.amount?a.name.localeCompare(b.name,'zh-CN'):a.amount>b.amount?-1:1);
          return `<section class="ks-currency-group" data-currency="${code}">
            ${section(`${period}收支 · ${code}`)}
            <div class="ks-summary">
              <div><span class="ks-note">收入 · ${code}</span><strong data-income>${money(total(group,'收入'),code)}</strong></div>
              <div><span class="ks-note">净支出 · ${code}</span><strong data-expense>${money(netExpense(group),code)}</strong></div>
            </div>
            ${categories.length?section(`净支出分类 · ${code}`)+`<div class="ks-stats-panel">${categories.map(({name,amount})=>`<div class="ks-stat-row"><div class="ks-row"><span>${escapeText(name)}</span><strong>${money(amount,code)}</strong></div></div>`).join('')}</div>`:''}
            ${group.length?'':empty}
          </section>`;
        }).join('')}
      </div><p class="ks-footnote">交互预览 · 各币种独立统计</p>`;
      return `${tabs}${picker}<section data-monthly-ledger aria-label="所选日期与钱包的流水" aria-live="polite">
        ${groups.map(({code,entries:group})=>`<div class="ks-currency-group">${moneyCard(code,group)}</div>`).join('')}
        ${section(`${period}流水`,`<span class="ks-note">${entries.length} 笔</span>`)}
        <div class="ks-record-list">
        ${entries.map(entry=>{
          const wallet=walletById(entry.walletId),category=entryCategory(entry);
          const lineLabel=entry.type==='退款'?`退款 · ${category}`:category;
          const glyph=entry.type==='退款'?'undo-2':category==='交通'?'train-front':category==='工资'?'briefcase-business':'receipt-text';
          return `<button type="button" class="ks-record cursor-interaction" data-entry-id="${entry.id}" aria-label="查看${escapeText(wallet.name)} ${escapeText(category)} ${entry.type} ${wallet.code} ${money(entry.cents,wallet.code)}详情">
            ${recordIcon(glyph)}
            <span class="ks-record-copy"><span class="ks-record-title">${escapeText(lineLabel)}<span class="ks-pill">${escapeText(wallet.name)}</span></span><span class="ks-record-meta ks-record-ellipsis">${includeYear?entry.date:entry.date.slice(5)} ${entry.time}${entry.note?` · ${escapeText(entry.note)}`:''}</span></span>
            <span class="ks-currency-amount"><strong>${entry.type==='支出'?'-':'+'}¥${money(entry.cents,wallet.code)}</strong><small>${wallet.code}</small></span>${icon('chevron-right')}
          </button>`;
        }).join('')}
        </div>
        ${entries.length?'':empty}
      </section><p class="ks-footnote">交互预览 · 示例数据</p>`;
    }

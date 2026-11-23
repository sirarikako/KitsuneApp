    function pageHome(){
      const today=state.records[demoToday]||[null,null,null],count=today.filter(Boolean).length,current=recommendedSlot();
      const groups=currencyGroups(monthEntries(demoToday.slice(0,7)).filter(e=>regularWallets().some(w=>w.id===e.walletId)),regularWallets())
        .sort((a,b)=>Number(b.code==='CNY')-Number(a.code==='CNY'));
      return `<div class="ks-home-cards">
        <section class="ks-home-card ks-daily" aria-label="今日用餐进度">
          <div class="ks-row ks-home-card-heading"><h3>今日用餐</h3><button type="button" class="ks-text-btn cursor-interaction" data-page="meals">全部记录 ${icon('chevron-right')}</button></div>
          <div class="ks-daily-count"><strong>${count}</strong><span class="ks-note"> / 3 餐已记录</span></div>
          <div class="ks-daily-states">${slots.map((slot,i)=>`<span class="ks-daily-state ${today[i]?'':'ks-pending'}">${icon(today[i]?'circle-check':'circle-dashed')}<span>${slot}${today[i]?'已记':'待记'}</span></span>`).join('')}</div>
          <button type="button" class="ks-primary ks-full cursor-interaction" data-home-meal data-meal="${current}">${icon(today[current]?'pencil':'plus')}${today[current]?'编辑':'记录'}${slots[current]}</button>
        </section>
        <section class="ks-home-card ks-home-money" aria-label="本月收支">
          <div class="ks-row ks-home-card-heading"><h3>本月收支</h3><button type="button" class="ks-text-btn cursor-interaction" data-page="ledger">查看账本 ${icon('chevron-right')}</button></div>
          ${groups.length ? groups.map(({code,entries})=>`<div class="ks-home-currency" data-currency="${code}">
            <span class="ks-money-caption">${code}</span>
            <span><small>净支出</small><strong data-expense>${money(netExpense(entries),code)}</strong></span>
            <span><small>收入</small><strong data-income>${money(total(entries,'收入'),code)}</strong></span>
          </div>`).join('') : '<p class="ks-home-empty">暂无常规钱包的收支</p>'}
          <button type="button" class="ks-primary ks-full cursor-interaction" data-action="add-entry">${icon('plus')}记一笔</button>
        </section>
      </div>`;
    }

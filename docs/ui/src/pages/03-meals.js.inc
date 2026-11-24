    function pageMeals(){
      const tabs=`<div class="ks-segments" aria-label="用餐视图">${['记录','统计'].map(t=>`<button type="button" data-meal-tab="${t}" aria-pressed="${state.mealTab===t}" class="cursor-interaction">${t}</button>`).join('')}</div>`;
      if(state.mealTab==='记录'){
        const labels=mealFilterLabels(state.mealFilters),filterButton=`<button type="button" class="ks-toolbar-action cursor-interaction" data-action="meal-filter">${icon('list-filter')}筛选${state.mealListMode&&labels.length?` (${labels.length})`:''}</button>`;
        if(!state.mealListMode)return `${tabs}<div class="ks-toolbar"><button type="button" class="ks-control ks-date-trigger cursor-interaction" aria-label="用餐日期 ${state.date}" data-action="open-meal-calendar">${state.date}${icon('calendar-days')}</button>${filterButton}</div>${week()}${section('三餐记录')}${mealCards()}`;
        const matches=filteredMeals(state.mealFilters).sort((a,b)=>b.date.localeCompare(a.date)||a.slot-b.slot);
        const resultActions=`<div class="ks-row"><span class="ks-note" data-match-count>${matches.length} 条</span>${labels.length?'<button type="button" class="ks-text-btn cursor-interaction" data-action="clear-meal-filters">清空条件</button>':''}</div>`;
        return `${tabs}<div class="ks-toolbar"><button type="button" class="ks-toolbar-action cursor-interaction" data-action="meal-daily">${icon('chevron-left')}按日浏览</button>${filterButton}</div>${labels.length?`<div class="ks-filter-chips" aria-label="已应用的筛选条件">${labels.map(label=>`<span class="ks-filter-chip">${escapeText(label)}</span>`).join('')}</div>`:''}${section('筛选结果',resultActions)}<div class="ks-record-list" data-meal-results aria-live="polite">${matches.map(mealResult).join('')}${!matches.length?'<p class="ks-empty-state">没有符合条件的记录<br>试试调整日期范围或筛选条件</p>':''}</div>`;
      }
      const filters=state.mealStatsFilters,labels=mealFilterLabels(filters,false);
      const matches=filteredMeals(filters),total=matches.length;
      const homeCount=matches.filter(m=>m.atHome).length,awayCount=total-homeCount;
      const rows=methodCounts(matches).filter(row=>row.count>0);
      const preparationNames=[...new Set(matches.flatMap(m=>m.categories))];
      const planBar=total?`<div class="ks-plan-visual" aria-hidden="true"><span class="ks-plan-yes" style="width:${homeCount/total*100}%"></span><span class="ks-plan-no" style="width:${awayCount/total*100}%"></span></div>`:'';
      const period=escapeText(mealDateSummary(filters));
      return `${tabs}
        <div class="ks-toolbar"><button type="button" class="ks-control ks-date-trigger cursor-interaction" data-action="choose-meal-month" aria-haspopup="dialog" aria-label="选择统计月份 ${period}"><span>${period}</span>${icon('chevron-down')}</button><button type="button" class="ks-toolbar-action cursor-interaction" data-action="meal-filter">${icon('list-filter')}筛选</button></div>
        <section data-meal-statistics aria-label="用餐统计" aria-live="polite">
          ${labels.length?`<div class="ks-filter-chips" aria-label="已应用的筛选条件">${labels.map(label=>`<span class="ks-filter-chip">${escapeText(label)}</span>`).join('')}</div>`:''}
          ${section('用餐概览')}
          <div class="ks-summary"><div><span class="ks-note">已记录</span><strong data-meal-stat-count>${total} 餐</strong></div><div><span class="ks-note">计划在家吃</span><strong>${homeCount} 餐</strong></div></div>
          <div class="ks-stats-panel"><div class="ks-stat-row"><div class="ks-row"><span>计划在家：是 ${homeCount} 餐</span><span>否 ${awayCount} 餐</span></div>${planBar}</div></div>
          ${section('用餐方式')}
          ${rows.length?`<div class="ks-stats-panel">${rows.map(({method,count})=>countBar(method,count,total)).join('')}</div>`:'<p class="ks-empty-state">没有符合条件的用餐记录</p>'}
          ${section('制作分类')}
          ${preparationNames.length?`<div class="ks-stats-panel">${preparationNames.map(name=>countBar(name,matches.filter(m=>m.categories.includes(name)).length,total,'次')).join('')}</div>`:'<p class="ks-empty-state">暂无制作分类记录</p>'}
        </section>
        <p class="ks-footnote">交互预览 · 示例数据</p>`;
    }

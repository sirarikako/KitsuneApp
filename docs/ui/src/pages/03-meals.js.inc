    function pageMeals(){
      const tabs=`<div class="ks-segments" aria-label="用餐视图">${['记录','统计'].map(t=>`<button type="button" data-meal-tab="${t}" aria-pressed="${state.mealTab===t}" class="cursor-interaction">${t}</button>`).join('')}</div>`;
      if(state.mealTab==='记录'){
        const labels=mealFilterLabels(),filterButton=`<button type="button" class="ks-toolbar-action cursor-interaction" data-action="meal-filter">${icon('list-filter')}筛选${state.mealListMode&&labels.length?` (${labels.length})`:''}</button>`;
        if(!state.mealListMode)return `${tabs}<div class="ks-toolbar"><button type="button" class="ks-control ks-date-trigger cursor-interaction" aria-label="用餐日期 ${state.date}" data-action="open-meal-calendar">${state.date}${icon('calendar-days')}</button>${filterButton}</div>${week()}${section('三餐记录')}${mealCards()}`;
        const matches=filteredMeals();
        const resultActions=`<div class="ks-row"><span class="ks-note" data-match-count>${matches.length} 条</span>${labels.length?'<button type="button" class="ks-text-btn cursor-interaction" data-action="clear-meal-filters">清空条件</button>':''}</div>`;
        return `${tabs}<div class="ks-toolbar"><button type="button" class="ks-toolbar-action cursor-interaction" data-action="meal-daily">${icon('chevron-left')}按日浏览</button>${filterButton}</div>${labels.length?`<div class="ks-filter-chips" aria-label="已应用的筛选条件">${labels.map(label=>`<span class="ks-filter-chip">${escapeText(label)}</span>`).join('')}</div>`:''}${section('筛选结果',resultActions)}<div class="ks-record-list" data-meal-results aria-live="polite">${matches.map(mealResult).join('')}${!matches.length?'<p class="ks-empty-state">没有符合条件的记录<br>试试调整日期范围或筛选条件</p>':''}</div>`;
      }
      const all=allMeals(),monthly=all.filter(m=>m.date.startsWith(state.mealMonth));
      const homeCount=monthly.filter(m=>m.atHome).length;
      const rows=methodCounts(monthly),allRows=methodCounts(all);
      const preparationNames=[...new Set(monthly.flatMap(m=>m.categories))];
      return `${tabs}${monthPicker('mealMonth','用餐统计')}
        <section data-monthly-meals aria-label="所选月份用餐统计" aria-live="polite">
          ${section('本月概览',`<span class="ks-note">${state.mealMonth.replace('-',' 年 ')} 月</span>`)}
          <div class="ks-summary"><div><span class="ks-note">已记录</span><strong data-monthly-count>${monthly.length} 餐</strong></div><div><span class="ks-note">计划在家吃</span><strong>${homeCount} 餐</strong></div></div>
          <div class="ks-stats-panel"><div class="ks-stat-row"><div class="ks-row"><span>计划在家：是 ${homeCount} 餐</span><span>否 ${monthly.length-homeCount} 餐</span></div><div class="ks-plan-visual" role="img" aria-label="计划在家吃：是 ${homeCount} 餐，否 ${monthly.length-homeCount} 餐"><span class="ks-plan-yes" style="width:${monthly.length?homeCount/monthly.length*100:0}%"></span><span class="ks-plan-no" style="width:${monthly.length?(monthly.length-homeCount)/monthly.length*100:0}%"></span></div></div></div>
          ${section('本月 · 用餐方式')}
          <div class="ks-stats-panel">${rows.map(({method,count})=>countBar(method,count,monthly.length)).join('')}</div>
          ${!monthly.length?'<p class="ks-empty-state">这个月还没有用餐记录</p>':''}
          ${section('本月 · 制作分类')}
          ${preparationNames.length?`<div class="ks-stats-panel">${preparationNames.map(name=>countBar(name,monthly.filter(m=>m.categories.includes(name)).length,monthly.length,'次')).join('')}</div>`:'<p class="ks-empty-state">暂无制作分类记录</p>'}
        </section>
        <section class="ks-all-methods" data-all-methods aria-label="全部记录按用餐方式统计">
          ${section('全部记录 · 用餐方式',`<span class="ks-note">共 ${all.length} 餐</span>`)}
          <p class="ks-note" style="margin:0 0 8px">所有月份累计，不受上方月份选择影响</p>
          <div class="ks-stats-panel">${allRows.map(({method,count})=>countBar(method,count,all.length)).join('')}</div>
        </section><p class="ks-footnote">交互预览 · 示例数据</p>`;
    }

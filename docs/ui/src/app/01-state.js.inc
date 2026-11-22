<script>
(() => {
  const root = document.getElementById('kitsune-preview');
  const escapeText = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const icon = name => `<i data-lucide="${name}" aria-hidden="true"></i>`;
  const navs = [['home','首页','house'],['meals','用餐','utensils'],['ledger','记账','wallet'],['settings','设置','sliders-horizontal']];
  const slots = ['早餐','午餐','晚餐'];
  const slotIcons = ['sunrise','sun','moon'];
  const normalizeHex=value=>{const text=String(value).trim().replace(/^#/,'');return /^[0-9a-f]{6}$/i.test(text)?`#${text.toUpperCase()}`:null;};
  const demoSystemSeed='#6B7650';
  const demoToday='2026-11-17';
  const maximumEntryMinor=9223372036854775807n;
  const themeModes=[['system','跟随系统'],['light','浅色'],['dark','深色']];
  const systemDark=window.matchMedia('(prefers-color-scheme: dark)');
  const reducedMotion=window.matchMedia('(prefers-reduced-motion: reduce)');
  const motion={primary:200,push:240,pop:220,card:280,sheet:250,dialog:180,local:150,appearance:200,theme:250,easing:'cubic-bezier(0.2, 0, 0, 1)'};
  const themeRoles=['--ks-bg','--ks-paper','--ks-soft','--ks-line','--ks-accent-bg','--ks-accent','--ks-ink','--ks-dim','--ks-good','--ks-danger','--ks-action-bg','--ks-action-text','--ks-on-danger','--ks-card-surface'];
  // Registered color roles interpolate on the existing phone rather than rebuilding its theme on each animation frame.
  const typedTheme=typeof CSS!=='undefined'&&typeof CSS.registerProperty==='function';
  if(typedTheme)for(const name of themeRoles){try{CSS.registerProperty({name,syntax:'<color>',inherits:true,initialValue:'#000000'});}catch{}}

  const phone=root.querySelector('.ks-phone');
  if(!phone)return;
  {
    const cardRadius=17;
    const defaultAppearance={mode:'system',colorSource:'PRESET',presetColor:'#c47a4a',customColor:'#C47A4A'};
    const style = {accent:'#c47a4a',mealHour:'auto',...defaultAppearance};
    const state = {page:'home', date:'2026-11-17', ledgerMonth:'2026-11',mealMonth:'2026-11',ledgerTab:'流水', mealTab:'记录', slot:2, atHome:null, method:null, categories:[], amount:'',calcOpen:false,calcExpression:'',entryType:'支出',entryDraft:{dateTime:'2026-11-17T09:41',note:'',category:'ge-food',walletId:'daily'},notice:'', mealReturn:'home', entries:[], records:{}};
    state.ledgerRange=null;state.ledgerRangeOpen=false;state.previewMode=null;
    const blankMealFilters=()=>({from:'',to:'',slot:'any',atHome:'any',method:'any',preparations:[],keyword:''});
    state.mealFilters=blankMealFilters();state.mealFilterDraft=blankMealFilters();state.mealListMode=false;
    const wallets=[{id:'daily',name:'日常钱包',code:'CNY',scale:2,opening:0n,separateAccounting:false},{id:'pocket',name:'零钱钱包',code:'CNY',scale:2,opening:50000n,separateAccounting:false},{id:'travel',name:'旅行钱包',code:'JPY',scale:0,opening:20000n,separateAccounting:true}];
    const walletById=id=>wallets.find(w=>w.id===id)||wallets[0];
    const entryWallet=()=>wallets.find(w=>w.id===state.entryDraft.walletId)||null;
    const defaultEntryWallet=()=>wallets.find(w=>w.id===state.lastWalletId&&!w.separateAccounting)||wallets.find(w=>!w.separateAccounting)||null;
    state.walletFilterIds=null;state.walletPickerOpen=false;state.walletDraftMode='REGULAR';state.walletDraftIds=[];state.lastWalletId='daily';
    // Financial categories are scoped by type and optional wallet; meal preparation categories are separate.
    const sharedFinancialCategories={
      EXPENSE:[{id:'ge-food',name:'餐饮',archived:false},{id:'ge-transport',name:'交通',archived:false},{id:'ge-daily',name:'日用',archived:false}],
      INCOME:[{id:'gi-salary',name:'工资',archived:false}]
    };
    const fallbackFinancialCategories={
      EXPENSE:{id:'ge-none',name:'未分类',archived:false,isDefault:true},
      INCOME:{id:'gi-none',name:'未分类',archived:false,isDefault:true}
    };
    const walletFinancialCategories=Object.fromEntries(wallets.map(w=>[w.id,{EXPENSE:[],INCOME:[]}]));
    wallets.forEach(w=>{w.incomeCategoryMode='DEFAULT';w.expenseCategoryMode='DEFAULT';});
    state.categoryWalletId='GLOBAL';state.financialScope='EXPENSE';state.editingFinancialCategoryId=null;state.categoryReturn='settings';state.nextFinancialCategoryId=1;
    const financialScopeOf=type=>type==='收入'?'INCOME':'EXPENSE';
    const scopeMode=(wallet,scope)=>wallet?.[scope==='INCOME'?'incomeCategoryMode':'expenseCategoryMode']||'DEFAULT';
    const allFinancialCategories=()=>[...sharedFinancialCategories.EXPENSE,...sharedFinancialCategories.INCOME,...Object.values(walletFinancialCategories).flatMap(v=>[...v.EXPENSE,...v.INCOME]),...Object.values(fallbackFinancialCategories)];
    const categoryById=id=>allFinancialCategories().find(c=>c.id===id)||null;
    const effectiveFinancialCategories=(walletId,type)=>{
      const wallet=wallets.find(w=>w.id===walletId);if(!wallet)return [];
      const scope=financialScopeOf(type);
      const collection=scopeMode(wallet,scope)==='CUSTOM'?walletFinancialCategories[wallet.id][scope]:sharedFinancialCategories[scope];
      return [...collection.filter(c=>!c.archived),fallbackFinancialCategories[scope]];
    };
    const categoryForEntry=entry=>entry.type==='退款'?financialEntries().find(e=>e.id===entry.originalExpenseId)?.category:entry.category;
    const categoryNameForId=id=>categoryById(id)?.name||'历史分类';
    const categoryDescriptionForId=id=>{
      const owner=wallets.find(w=>walletFinancialCategories[w.id].EXPENSE.some(c=>c.id===id)||walletFinancialCategories[w.id].INCOME.some(c=>c.id===id));
      return owner?categoryNameForId(id)+' · '+owner.name:categoryNameForId(id);
    };
    const chooseFinancialCategory=(walletId,type,old)=>effectiveFinancialCategories(walletId,type).some(c=>c.id===old)?old:'';

    const savedState=window.openai?.widgetState?.privateContent?.kitsune;
    // Accept only the original primary preview preferences from old saved arrays.
    const saved=Array.isArray(savedState)?savedState[0]:savedState;
    state.deletedEntryIds=new Set();state.nextEntryId=1;state.deletePending=null;
    if(saved){const s=saved;if(['light','dark','system'].includes(s.mode))style.mode=s.mode;for(const key of ['ledgerMonth','mealMonth'])if(/^\d{4}-(0[1-9]|1[0-2])$/.test(s[key]||'')&&s[key].slice(0,4)!=='0000')state[key]=s[key];if(s.walletFilterIds==='ALL')state.walletFilterIds='ALL';else if(Array.isArray(s.walletFilterIds)){const ids=wallets.filter(w=>s.walletFilterIds.includes(w.id)).map(w=>w.id);if(ids.length)state.walletFilterIds=ids;}if(Array.isArray(s.separateWalletIds))wallets.forEach(w=>{w.separateAccounting=s.separateWalletIds.includes(w.id);});const f=s.mealFilters;if(f&&typeof f==='object'){for(const key of ['from','to'])if(typeof f[key]==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(f[key]))state.mealFilters[key]=f[key];if(['any','0','1','2'].includes(f.slot))state.mealFilters.slot=f.slot;if(['any','yes','no'].includes(f.atHome))state.mealFilters.atHome=f.atHome;if(typeof f.method==='string')state.mealFilters.method=f.method.slice(0,80);if(typeof f.keyword==='string')state.mealFilters.keyword=f.keyword.slice(0,100);if(Array.isArray(f.preparations))state.mealFilters.preparations=f.preparations.filter(x=>typeof x==='string').slice(0,30).map(x=>x.slice(0,80));state.mealListMode=s.mealListMode===true;}}
    state.records['2026-11-17'] = [{atHome:true,method:'下厨',categories:[],dishes:'燕麦粥、煎蛋',note:''},{atHome:false,method:'外卖',categories:[],dishes:'照烧鸡腿饭',note:''},null];
    if(saved){const s=saved;if(['PRESET','CUSTOM','SYSTEM'].includes(s.colorSource))style.colorSource=s.colorSource;if(normalizeHex(s.presetColor))style.presetColor=s.presetColor.toLowerCase();if(normalizeHex(s.customColor))style.customColor=normalizeHex(s.customColor);}
    const isDate=value=>/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value)&&value.slice(0,4)!=='0000'&&new Date(value).toJSON()?.slice(0,10)===value;
    const savedRange=saved?.ledgerRange;
    if(savedRange&&isDate(savedRange.from)&&isDate(savedRange.to)&&savedRange.from<=savedRange.to)state.ledgerRange={from:savedRange.from,to:savedRange.to};
    const appliedAppearance=()=>({mode:style.mode,colorSource:style.colorSource,presetColor:style.presetColor,customColor:style.customColor});
    const pendingAppearance=()=>{
      const draft=state.appearanceDraft,customColor=normalizeHex(draft.customColor);
      return {...draft,customColor:draft.colorSource==='CUSTOM'?customColor:customColor||style.customColor};
    };
    const appearanceChanged=pending=>Object.keys(pending).some(key=>pending[key]!==style[key]);
    const resetAppearance=()=>{state.appearanceDraft=appliedAppearance();state.previewMode=null;};
    resetAppearance();
    const colorSeed=theme=>theme.colorSource==='SYSTEM'?demoSystemSeed:theme.colorSource==='CUSTOM'?(normalizeHex(theme.customColor)||style.customColor):theme.presetColor;
    state.records['2026-10-31'] = [{atHome:true,method:'下厨',categories:['炒菜'],dishes:'番茄炒蛋',note:''},{atHome:false,method:'外卖',categories:[],dishes:'牛肉面',note:''},{atHome:false,method:null,categories:[],dishes:'和朋友吃饭',note:''}];
    const exampleEntries = [
      {date:'2026-11-17',time:'12:30',type:'支出',category:'餐饮',cents:78650,note:'本月餐饮示例'},
      {date:'2026-11-16',time:'18:20',type:'支出',category:'日用',cents:32000,note:'日用品'},
      {date:'2026-11-15',time:'08:30',type:'支出',category:'交通',cents:18000,note:'交通卡充值'},
      {date:'2026-11-01',time:'09:00',type:'收入',category:'工资',cents:850000,note:'工资到账'},
      {date:'2026-10-31',time:'19:10',type:'支出',category:'餐饮',cents:62000,note:'本月餐饮示例'},
      {date:'2026-10-29',time:'18:20',type:'支出',category:'日用',cents:16000,note:'日用品'},
      {date:'2026-10-28',time:'08:30',type:'支出',category:'交通',cents:18000,note:'交通卡充值'},
      {date:'2026-10-01',time:'09:00',type:'收入',category:'工资',cents:850000,note:'工资到账'},
      {date:'2026-11-16',time:'13:10',type:'支出',category:'餐饮',cents:1200,note:'旅行午餐',walletId:'travel'},
      {date:'2026-11-15',time:'09:00',type:'收入',category:'未分类',cents:5000,note:'旅行备用金',walletId:'travel'},
      {date:'2026-11-17',time:'20:15',type:'退款',originalExpenseId:'example-1',cents:3000,note:'退回日常钱包'}
    ];
    const sampleCategoryIds={餐饮:'ge-food',交通:'ge-transport',日用:'ge-daily',工资:'gi-salary',未分类:'gi-none'};
    const sampleEntries=exampleEntries.map((entry,i)=>({
      walletId:i===1||i===5?'pocket':'daily',...entry,id:`example-${i}`,cents:BigInt(entry.cents),
      category:entry.type==='退款'?null:(entry.category==='未分类'&&entry.type==='支出'?'ge-none':sampleCategoryIds[entry.category])
    }));
    const financialEntries=()=>[...sampleEntries,...state.entries].filter(entry=>!state.deletedEntryIds.has(entry.id));
    const monthEntries = month => financialEntries().filter(e=>e.date.startsWith(month));
    function formatMinor(minor,scale,grouping){
      const factor=10n**BigInt(scale),absolute=minor<0n?-minor:minor;
      const whole=(absolute/factor).toLocaleString('en-US',{useGrouping:grouping});
      const fraction=(absolute%factor).toString().padStart(scale,'0');
      return `${minor<0n?'-':''}${whole}${scale?'.'+fraction:''}`;
    }
    const money=(minor,code)=>formatMinor(minor,wallets.find(w=>w.code===code).scale,true);
    const inputAmount=minor=>formatMinor(minor,entryWallet()?.scale??2,false);
    const total=(entries,type)=>entries.filter(e=>e.type===type).reduce((sum,e)=>sum+e.cents,0n);
    const netExpense=entries=>total(entries,'支出')-total(entries,'退款');
    const entryCategory=entry=>categoryNameForId(categoryForEntry(entry));
    const regularWallets=()=>wallets.filter(w=>!w.separateAccounting);
    const selectedWallets=()=>state.walletFilterIds===null?regularWallets():state.walletFilterIds==='ALL'?[...wallets]:wallets.filter(w=>state.walletFilterIds.includes(w.id));
    const matchesLedgerScope=entry=>{
      const range=state.ledgerRange;
      const dateMatches=range?range.from<=entry.date&&entry.date<=range.to:entry.date.startsWith(state.ledgerMonth);
      return dateMatches&&selectedWallets().some(w=>w.id===entry.walletId);
    };
    const currencyGroups=(entries,scope)=>[...new Set(scope.map(w=>w.code))].map(code=>({code,entries:entries.filter(e=>walletById(e.walletId).code===code)}));
    const allMeals = () => Object.entries(state.records).flatMap(([date,records])=>records.flatMap((record,slot)=>record?[{...record,date,slot}]:[]));
    const mealFilterLabels=()=>{const f=state.mealFilters,labels=[];if(f.from||f.to)labels.push(`${f.from||'最早'} ～ ${f.to||'至今'}`);if(f.slot!=='any')labels.push(slots[Number(f.slot)]);if(f.atHome!=='any')labels.push(`计划在家：${f.atHome==='yes'?'是':'否'}`);if(f.method!=='any')labels.push(f.method==='none'?'方式未填写':f.method);labels.push(...f.preparations);if(f.keyword)labels.push(`关键词：${f.keyword}`);return labels;};
    const filteredMeals=()=>{const f=state.mealFilters,keyword=f.keyword.toLocaleLowerCase();return allMeals().filter(m=>(!f.from||m.date>=f.from)&&(!f.to||m.date<=f.to)&&(f.slot==='any'||m.slot===Number(f.slot))&&(f.atHome==='any'||m.atHome===(f.atHome==='yes'))&&(f.method==='any'||(f.method==='none'?!m.method:m.method===f.method))&&(!f.preparations.length||f.preparations.some(c=>m.categories.includes(c)))&&(!keyword||`${m.dishes}\n${m.note}`.toLocaleLowerCase().includes(keyword))).sort((a,b)=>b.date.localeCompare(a.date)||a.slot-b.slot);};
    const records = () => state.records[state.date] || [null,null,null];
    const persist = () => {
      const snapshot=phone.ksSnapshot();
      window.openai?.setWidgetState?.({modelContent:{当前页面:snapshot.page,用餐筛选:snapshot.mealListMode?snapshot.mealFilters:null},privateContent:{kitsune:snapshot}})?.catch(()=>{});
    };
    phone.ksSnapshot=()=>({page:navs.some(n=>n[0]===state.page)?state.page:'home',...appliedAppearance(),ledgerMonth:state.ledgerMonth,ledgerRange:state.ledgerRange,mealMonth:state.mealMonth,walletFilterIds:state.walletFilterIds,separateWalletIds:wallets.filter(w=>w.separateAccounting).map(w=>w.id),mealListMode:state.mealListMode,mealFilters:state.mealFilters});
    // Android applies these same presentation roles for preset and custom seeds.
    function makePalette(seed,source){
      const rgb=h=>h.replace("#","").match(/../g).map(x=>parseInt(x,16));
      const mix=(a,b,t)=>"#"+rgb(a).map((v,i)=>Math.round(v*t+rgb(b)[i]*(1-t)).toString(16).padStart(2,"0")).join("");
      const luminance=h=>rgb(h).map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4}).reduce((s,v,i)=>s+v*[.2126,.7152,.0722][i],0);
      const contrast=(a,b)=>(Math.max(luminance(a),luminance(b))+.05)/(Math.min(luminance(a),luminance(b))+.05);
      const safe=(color,backgrounds,light)=>{const end=light?"#000000":"#ffffff";for(let i=0;i<=100;i++){const candidate=mix(color,end,1-i/100);if(backgrounds.every(bg=>contrast(candidate,bg)>=4.6))return candidate;}throw Error("unreachable contrast");};
      function surfaceTones(light){
        if(source==='PRESET')return [
          mix(seed,light?"#fafafa":"#151515",light?.12:.10),
          mix(seed,light?"#ffffff":"#1f1f1f",light?.045:.14),
          mix(seed,light?"#f3f3f3":"#292929",light?.15:.17),
          mix(seed,light?"#e5e5e5":"#343434",light?.18:.19)
        ];
        // The wallpaper demo uses illustrative colors for the tones Android gets from the system.
        if(source==='SYSTEM')return [
          mix(seed,light?"#fbfcf7":"#191c17",light?.06:.12),
          mix(seed,light?"#ffffff":"#262921",light?.03:.16),
          mix(seed,light?"#f5f6ed":"#292d24",light?.15:.22),
          mix(seed,light?"#e3e5d9":"#41463a",light?.21:.24)
        ];
        return light?["#f6f3ed","#fffcf7","#eee7dc","#e5ded4"]:["#201e1b","#2a2723","#383129","#443c34"];
      }
      const modes=[true,false].map(light=>{
        const [bg,paper,soft,line]=surfaceTones(light);
        const selected=mix(seed,light?(source==='SYSTEM'?"#fffcf7":paper):"#211e1a",light?.18:.23);
        const surfaces=[bg,paper,soft,selected];
        const accent=safe(mix(seed,light?"#342c23":"#fff4e6",light?.72:.45),surfaces,light);
        return {bg,paper,soft,line,selected,accent,ink:safe(light?"#302b26":"#f3eae1",surfaces,light),dim:safe(light?"#76695e":"#baaea2",surfaces,light),good:safe(light?"#46624a":"#b7d0ae",surfaces,light),danger:safe(light?"#a63832":"#ffb4ab",surfaces,light),onAction:paper,onDanger:safe(light?"#fff8f7":"#451714",[safe(light?"#a63832":"#ffb4ab",surfaces,light)],!light)};
      });
      return modes;
    }
    const paintColors=(target,seed,source)=>{
      const [light,dark]=makePalette(seed,source);
      const value=key=>`light-dark(${light[key]},${dark[key]})`;
      const roles={'--ks-bg':'bg','--ks-paper':'paper','--ks-soft':'soft','--ks-line':'line','--ks-accent-bg':'selected','--ks-accent':'accent','--ks-ink':'ink','--ks-dim':'dim','--ks-good':'good','--ks-danger':'danger','--ks-action-bg':'accent','--ks-action-text':'onAction','--ks-on-danger':'onDanger','--ks-card-surface':'paper'};
      target.style.setProperty('--ks-seed',seed);
      for(const [key,role] of Object.entries(roles))target.style.setProperty(key,value(role));
      target.style.setProperty('--ks-card-border','1px solid var(--ks-line)');
    };
    const applyStyle = () => {
      style.accent=colorSeed(style);
      phone.style.setProperty('--ks-radius',`${cardRadius}px`);
      paintColors(phone,style.accent,style.colorSource);
      phone.style.colorScheme=style.mode==='system'?'inherit':style.mode;
    };
    const recordIcon = glyph => `<span class="ks-record-icon">${icon(glyph)}</span>`;
    const formFooter = (actions,note='') => `<div class="ks-form-footer"><p class="ks-error" role="alert"></p>${actions}${note?`<p class="ks-footnote">${note}</p>`:''}</div>`;
    const saveButton = label => `<button type="submit" class="ks-primary ks-full cursor-interaction">${label}</button>`;
    const deleteButton = (kind,label) => `<button type="button" class="ks-delete-btn cursor-interaction" data-action="delete-${kind}">${icon('trash-2')}${label}</button>`;
    const mealCards = () => `<div class="ks-record-list">${slots.map((slot,i)=>{
      const m=records()[i];
      return `<button type="button" class="ks-record ${m?'':'ks-empty'} cursor-interaction" data-meal="${i}">${recordIcon(slotIcons[i])}<span class="ks-record-copy"><span class="ks-record-title">${slot}${m?`<span class="ks-pill">${escapeText(m.method||'方式未填写')}</span>`:''}</span><span class="ks-record-meta">${m?escapeText(m.dishes||'未填写菜品'):'添加记录'}</span></span>${icon(m?'chevron-right':'plus')}</button>`;
    }).join('')}</div>`;
    function moneyCard(code,entries){
      const period=state.ledgerRange?'所选期间':`${Number(state.ledgerMonth.slice(5))} 月`;
      return `<div class="ks-money-card" data-currency="${code}">
        <span class="ks-money-caption">${period}净支出 · ${code}</span>
        <div class="ks-amount" data-expense>${money(netExpense(entries),code)}</div>
        <div class="ks-money-meta"><span>收入 <span data-income>${money(total(entries,'收入'),code)}</span></span></div>
      </div>`;
    }
    function week(){
      const selected=new Date(state.date+'T00:00:00Z');
      const monday=selected.getUTCDate()-(selected.getUTCDay()+6)%7;
      const days=['一','二','三','四','五','六','日'].map((label,i)=>{
        const day=new Date(selected);day.setUTCDate(monday+i);
        const month=day.getUTCMonth()+1,number=day.getUTCDate(),iso=day.toISOString().split('T')[0];
        const available=day.getUTCFullYear()>=1&&day.getUTCFullYear()<=9999;
        return `<button class="ks-day cursor-interaction" data-date="${iso}" type="button" ${available?'':'disabled'} aria-label="${month} 月 ${number} 日，星期${label}" aria-pressed="${state.date===iso}"><small>${label}</small><span>${number}</span></button>`;
      }).join('');
      return `<div class="ks-week" role="group" aria-label="选择日期">${days}</div>`;
    }
    const section = (title,extra='') => `<div class="ks-section ks-row"><h3>${title}</h3>${extra}</div>`;
    const choice = (label,value,current,action) => `<button type="button" class="ks-choice cursor-interaction" data-${action}="${escapeText(value)}" aria-pressed="${current===value}">${label}</button>`;
    const monthPicker = (key,label) => `<div class="ks-month-picker" role="group" aria-label="${label}月份选择"><button type="button" class="ks-icon-btn cursor-interaction" data-month-step="-1" data-month-key="${key}" aria-label="${label}上个月">${icon('chevron-left')}</button><label class="ks-month-field"><input type="month" value="${state[key]}" data-month-input="${key}" aria-label="${label}月份" min="0001-01" max="9999-12"></label><button type="button" class="ks-icon-btn cursor-interaction" data-month-step="1" data-month-key="${key}" aria-label="${label}下个月">${icon('chevron-right')}</button></div>`;
    function ledgerDatePicker(){
      const range=state.ledgerRange;
      const label=state.ledgerTab==='流水'?'流水':'收支统计';
      return `<div>
        ${range?'':monthPicker('ledgerMonth',label)}
        <div class="ks-toolbar"><button type="button" class="ks-toolbar-action ks-scope" data-action="edit-ledger-range" aria-expanded="${state.ledgerRangeOpen}">
          <span>${icon('calendar-range')}日期范围</span>
          <span>${range?`${range.from} 至 ${range.to}`:'自定义'}${icon('chevron-down')}</span>
        </button></div>
        ${state.ledgerRangeOpen?`<form class="ks-form ks-ledger-range-form" data-ledger-range-form>
          <div class="ks-field-row">
            <label><span class="ks-label">开始日期</span><input type="date" name="from" class="ks-control" value="${range?.from||''}" min="0001-01-01" max="9999-12-31" required></label>
            <label><span class="ks-label">结束日期</span><input type="date" name="to" class="ks-control" value="${range?.to||''}" min="0001-01-01" max="9999-12-31" required></label>
          </div>
          ${formFooter(`<div class="ks-form-actions"><button type="button" class="ks-choice" data-action="cancel-ledger-range">取消</button>${saveButton('应用日期')}</div>`)}
        </form>`:''}
        ${range?`<button type="button" class="ks-text-btn" data-action="ledger-month-mode">${icon('calendar-days')}返回按月</button>`:''}
      </div>`;
    }
    const walletScope=()=>{const chosen=selectedWallets(),label=state.walletFilterIds===null?'常规钱包':state.walletFilterIds==='ALL'?'全部钱包（含独立核算）':chosen.length===1?`${chosen[0].name} · ${chosen[0].code}`:`已选 ${chosen.length} 个钱包`;return `<div class="ks-toolbar"><button type="button" class="ks-toolbar-action ks-scope cursor-interaction" data-action="choose-wallets" aria-label="选择统计与流水的钱包范围"><span>${icon('wallet')}钱包范围</span><span>${escapeText(label)}${icon('chevron-down')}</span></button></div>${state.walletFilterIds&&chosen.length>1?`<p class="ks-filter-context">${chosen.map(w=>`${escapeText(w.name)} · ${w.code}`).join('、')}</p>`:''}`;};
    const methodCounts = meals => [...new Set(['下厨','外卖','未填写',...meals.map(m=>m.method||'未填写')])].map(method=>({method,count:meals.filter(m=>(m.method||'未填写')===method).length}));
    const recommendedSlot = () => {const hour=style.mealHour==='auto'?new Date().getHours():Number(style.mealHour);return hour<11?0:hour<17?1:2;};
    const countBar = (label,count,denominator,unit='餐') => {const percent=denominator?Math.round(count/denominator*100):0;return `<div class="ks-stat-row"><div class="ks-row"><span>${escapeText(label)}</span><span><strong>${count} ${unit}</strong><small>${percent}%</small></span></div><div class="ks-bar-track" role="meter" aria-label="${escapeText(label)}：${count}${unit}，占${percent}%" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${percent}"><div class="ks-bar-fill" style="width:${denominator?count/denominator*100:0}%"></div></div></div>`;};
    const mealResult=m=>{
      const method=m.method||'方式未填写',dishes=m.dishes||'未填写菜品';
      const meta=[`计划在家：${m.atHome?'是':'否'}`,...m.categories,m.note?`备注：${m.note.replace(/\s+/g,' ')}`:''].filter(Boolean).join(' · ');
      const summary=[m.date,slots[m.slot],method,dishes,meta].join('，');
      return `<button type="button" class="ks-record cursor-interaction" data-meal="${m.slot}" data-meal-date="${m.date}" aria-label="查看记录：${escapeText(summary)}" data-tooltip="${escapeText(summary)}">
        ${recordIcon(slotIcons[m.slot])}
        <span class="ks-record-copy"><span class="ks-record-title"><span class="ks-record-ellipsis">${escapeText(dishes)}</span></span><span class="ks-record-meta">${m.date} · ${slots[m.slot]} <span class="ks-pill">${escapeText(method)}</span></span><span class="ks-record-meta ks-record-ellipsis">${escapeText(meta)}</span></span>
        ${icon('chevron-right')}
      </button>`;
    };

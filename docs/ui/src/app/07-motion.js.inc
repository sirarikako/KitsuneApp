    // Motion is deliberately separate from the example business state. Leaving copies are inert,
    // contain no business selectors, and are removed on completion or the next render.
    let rendered=null,rendering=false,recordOrigin=null,entrance=null,themeAnimation=null;
    const running=new Set();
    const play=(node,frames,duration,done=()=>{})=>{
      if(!node||reducedMotion.matches||!node.animate){done();return null;}
      const animation=node.animate(frames,{duration,easing:motion.easing,fill:'none'});
      running.add(animation);
      animation.onfinish=animation.oncancel=()=>{running.delete(animation);done();};
      return animation;
    };
    const stopMotion=()=>{
      for(const animation of [...running])animation.cancel();
      running.clear();
      phone.querySelectorAll('.ks-motion-copy,.ks-motion-appearance-copy').forEach(node=>node.remove());
      phone.style.minHeight='';
    };
    reducedMotion.addEventListener('change',()=>{if(reducedMotion.matches){stopMotion();themeAnimation?.cancel();}});
    const rect=node=>{
      if(!node)return null;
      const r=node.getBoundingClientRect(),p=phone.getBoundingClientRect();
      return r.width&&r.height?{x:r.left-p.left-phone.clientLeft,y:r.top-p.top-phone.clientTop,w:r.width,h:r.height}:null;
    };
    const visible=node=>{
      if(!node)return false;
      const r=node.getBoundingClientRect(),p=phone.getBoundingClientRect();
      return r.width>0&&r.height>0&&r.left>=p.left-1&&r.right<=p.right+1&&r.top>=Math.max(0,p.top)&&r.bottom<=Math.min(window.innerHeight,p.bottom);
    };
    const cloneVisual=node=>{
      const copy=node.cloneNode(true);
      for(const el of [copy,...copy.querySelectorAll('*')]){
        el.removeAttribute('id');
        for(const a of [...el.attributes])if(a.name.startsWith('data-'))el.removeAttribute(a.name);
        el.removeAttribute('autofocus');
      }
      copy.inert=true;copy.setAttribute('aria-hidden','true');
      return copy;
    };
    const ghost=(nodes,modal=false)=>{
      const group=document.createElement('div');
      group.className='ks-motion-copy'+(modal?' ks-motion-modal-copy':'');
      group.inert=true;group.setAttribute('aria-hidden','true');
      for(const node of nodes){
        const r=rect(node);if(!r)continue;
        const copy=cloneVisual(node);
        Object.assign(copy.style,{position:'absolute',inset:'auto',left:r.x+'px',top:r.y+'px',width:r.w+'px',height:r.h+'px',margin:'0',boxSizing:'border-box'});
        const ledgerViewport=rect(node.closest('.ks-ledger-screen .ks-main'));
        if(ledgerViewport)copy.style.clipPath=clip(r,ledgerViewport,0);
        if(node.matches('.ks-ledger-screen'))group.ledgerPosition=node.querySelector('.ks-main').scrollTop;
        group.append(copy);
      }
      return group.childElementCount?group:null;
    };
    const attachGhost=group=>{
      if(group){
        phone.append(group);
        if(group.ledgerPosition)group.querySelector('.ks-ledger-screen .ks-main').scrollTop=group.ledgerPosition;
      }
      return group;
    };
    const discard=group=>()=>group?.remove();
    const pageNode=()=>phone.querySelector(':scope > .ks-page-motion');
    const modalNode=()=>phone.querySelector(':scope > .ks-modal-overlay');
    const modalKind=()=>state.deletePending?'dialog':state.calcOpen?'calculator':state.walletPickerOpen?'wallet':state.ledgerPeriodDraft?'ledger-period':state.ledgerFiltersOpen?'ledger-filters':state.githubDemo.review?'github-review':state.mealCalendar?'date-calendar':null;
    const primary=page=>navs.some(n=>n[0]===page);
    const localKey=()=>state.page==='meals'?JSON.stringify([state.mealTab,state.date,state.mealMonth,state.mealListMode,state.mealFilters]):state.page==='ledger'?JSON.stringify([state.ledgerTab,state.ledgerMonth,state.ledgerRange,state.walletFilterIds,state.ledgerFilters]):'';
    const localNodes=()=>{
      const main=phone.querySelector('.ks-main');if(!main)return [];
      const explicit=main.querySelector('[data-monthly-meals],[data-monthly-ledger],[data-ledger-statistics],[data-meal-results]');
      if(explicit)return [explicit];
      return [...main.children].filter(n=>n.matches('.ks-record-list,.ks-currency-group,.ks-wallet-cards'));
    };
    const inFade=[{opacity:0,offset:0},{opacity:0,offset:.3},{opacity:1,offset:1}];
    const outFade=[{opacity:1,offset:0},{opacity:0,offset:.45},{opacity:0,offset:1}];
    const clip=(card,box,r)=>'inset('+Math.max(0,card.y-box.y)+'px '+Math.max(0,box.x+box.w-card.x-card.w)+'px '+Math.max(0,box.y+box.h-card.y-card.h)+'px '+Math.max(0,card.x-box.x)+'px round '+r+'px)';
    const fullClip='inset(0px 0px 0px 0px round 0px)';
    const originNode=()=>{
      if(!recordOrigin||recordOrigin.page!==state.page)return null;
      if(recordOrigin.id)return [...phone.querySelectorAll('[data-entry-id]')].find(n=>n.dataset.entryId===recordOrigin.id);
      if(state.date!==recordOrigin.date&&!state.mealListMode)return null;
      return [...phone.querySelectorAll('.ks-record[data-meal]')].find(n=>n.dataset.meal===recordOrigin.slot&&(n.dataset.mealDate||state.date)===recordOrigin.date&&!n.classList.contains('ks-empty'));
    };
    function animatePage(previous,current,oldPage,sourceRect,oldNav,oldHeight){
      const node=pageNode(),next=current.page;
      if(!node||!previous||previous.page===next){oldPage?.remove();return;}
      const oldIsPrimary=primary(previous.page),newIsPrimary=primary(next);
      if(oldIsPrimary&&newIsPrimary){
        attachGhost(oldPage);play(oldPage,outFade,motion.primary,discard(oldPage));
        play(node,[{opacity:0,transform:'scale(.96)'},{opacity:0,offset:.25,transform:'scale(.96)'},{opacity:1,transform:'scale(1)'}],motion.primary);return;
      }
      const entering=current.stack.length>previous.stack.length;
      // Keep the departing bar under the page layer at its last measured position.
      if(oldNav){phone.style.minHeight=oldHeight+'px';oldNav.style.zIndex='0';attachGhost(oldNav);}
      const finishEnter=()=>{
        if(oldNav?.isConnected){oldNav.remove();phone.style.minHeight='';}
        node.style.zIndex='';node.style.position='';
      };
      // The preview grows with its content. Extend a shorter outgoing subpage over the restored bar.
      if(newIsPrimary&&oldPage){
        const nav=rect(phone.querySelector(':scope > .ks-nav')),copy=oldPage.firstElementChild;
        if(nav)copy.style.height=Math.max(parseFloat(copy.style.height),nav.y+nav.h-parseFloat(copy.style.top))+'px';
      }
      const box=rect(node),target=!entering?originNode():null;
      // Use the real card only when visible and inside the destination page; never guess its position.
      const card=entering?sourceRect:visible(target)?rect(target):null;
      if(card&&box&&recordOrigin&&oldPage&&(!entering||(card.x>=box.x&&card.y>=box.y&&card.x+card.w<=box.x+box.w&&card.y+card.h<=box.y+box.h))){
        const radius=cardRadius,copy=oldPage.firstElementChild;
        if(entering){
          attachGhost(oldPage);oldPage.style.zIndex='0';node.style.position='relative';node.style.zIndex='1';
          play(oldPage,[{opacity:1},{opacity:0}],motion.card,discard(oldPage));
          play(node,[{clipPath:clip(card,box,radius)},{clipPath:fullClip}],motion.card,finishEnter);
        }else{
          const oldBox={x:parseFloat(copy.style.left),y:parseFloat(copy.style.top),w:parseFloat(copy.style.width),h:parseFloat(copy.style.height)};
          // If the original row moved outside the old page's bounds, fall back to the standard pop.
          if(card.y>=oldBox.y&&card.y+card.h<=oldBox.y+oldBox.h){
            attachGhost(oldPage);play(copy,[{clipPath:fullClip},{clipPath:clip(card,oldBox,radius)}],motion.card,discard(oldPage));
          }else{attachGhost(oldPage);play(oldPage,[{transform:'translateX(0)'},{transform:'translateX(100%)'}],motion.pop,discard(oldPage));play(node,[{transform:'translateX(-12%)'},{transform:'translateX(0)'}],motion.pop);}
        }
        return;
      }
      attachGhost(oldPage);
      if(entering){
        if(oldPage){oldPage.style.zIndex='0';node.style.position='relative';node.style.zIndex='1';play(oldPage,[{transform:'translateX(0)'},{transform:'translateX(-12%)'}],motion.push,discard(oldPage));}
        play(node,[{transform:'translateX(100%)'},{transform:'translateX(0)'}],motion.push,finishEnter);
      }else{
        play(oldPage,[{transform:'translateX(0)'},{transform:'translateX(100%)'}],motion.pop,discard(oldPage));play(node,[{transform:'translateX(-12%)'},{transform:'translateX(0)'}],motion.pop);
      }
    }
    function animateModal(previous,oldModal){
      const kind=modalKind(),node=modalNode();
      if(previous===kind)return;
      const opening=!!kind,group=opening?node:attachGhost(oldModal);
      const overlay=opening?node:group?.firstElementChild;
      if(!overlay)return;
      const panel=overlay.firstElementChild,variant=opening?kind:previous,dialog=variant!=='calculator',duration=dialog?motion.dialog:motion.sheet;
      play(overlay,opening?[{opacity:0},{opacity:1}]:[{opacity:1},{opacity:0}],duration,opening?()=>{}:discard(group));
      play(panel,dialog?(opening?[{transform:'scale(.96)'},{transform:'scale(1)'}]:[{transform:'scale(1)'},{transform:'scale(.96)'}]):(opening?[{transform:'translateY(100%)'},{transform:'translateY(0)'}]:[{transform:'translateY(0)'},{transform:'translateY(100%)'}]),duration);
    }
    function expandAppearance(editor){
      if(!editor||editor.hidden||reducedMotion.matches)return;
      const height=editor.getBoundingClientRect().height;
      if(height)play(editor,[{height:'0px',opacity:0,overflow:'hidden'},{height:height+'px',opacity:1,overflow:'hidden'}],motion.appearance);
    }
    function collapseAppearance(editor,copy,height){
      if(!copy||!height||reducedMotion.matches)return;
      copy.classList.add('ks-motion-appearance-copy');copy.hidden=false;
      editor.after(copy);
      play(copy,[{height:height+'px',opacity:1},{height:'0px',opacity:0}],motion.appearance,discard(copy));
    }
    const themeValues=()=>Object.fromEntries(themeRoles.map(name=>[name,getComputedStyle(phone).getPropertyValue(name).trim()]));
    const resolvedMode=()=>style.mode==='system'?(systemDark.matches?'dark':'light'):style.mode;
    function applyAnimatedAppearance(next){
      const from=typedTheme?themeValues():null,mode=resolvedMode();
      themeAnimation?.cancel();Object.assign(style,next);resetAppearance();applyStyle();
      // A light/dark crossing switches semantic foreground/background pairs together; interpolating
      // opposed text and surfaces would create a low-contrast midpoint.
      if(from&&mode===resolvedMode()&&!reducedMotion.matches&&phone.animate){
        themeAnimation=phone.animate([from,themeValues()],{duration:motion.theme,easing:motion.easing});
      }
    }

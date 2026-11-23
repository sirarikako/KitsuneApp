<style>
  #kitsune-preview .ks-stage{min-height:830px;display:flex;justify-content:center;align-items:flex-start;padding:24px 10px 18px;box-sizing:border-box}
  #kitsune-preview .ks-phone{--ks-seed:#c47a4a;--ks-bg:light-dark(#f4ebe5,#271f1a);--ks-paper:light-dark(#fcf9f7,#362c25);--ks-ink:light-dark(#302b26,#f3eae1);--ks-dim:light-dark(#6e6257,#baaea2);--ks-line:light-dark(#dfd2c9,#4f4138);--ks-soft:light-dark(#ece1da,#43372f);--ks-accent:light-dark(#895837,#e4bda0);--ks-accent-bg:light-dark(#f2e2d8,#463325);--ks-good:light-dark(#46624a,#b7d0ae);--ks-good-bg:light-dark(#e7eddf,#303e2e);--ks-expense:light-dark(#82551f,#e8b276);--ks-danger:light-dark(#a63832,#ffb4ab);--ks-card-surface:var(--ks-paper);--ks-card-border:1px solid var(--ks-line);--ks-radius:17px;--ks-space-1:4px;--ks-space-2:8px;--ks-space-3:12px;--ks-space-4:16px;--ks-space-6:24px;--ks-control-radius:12px;--ks-dialog-radius:24px;--ks-control-height:46px;--ks-icon-size:18px;--ks-font-body:14px;--ks-font-note:12px;--ks-font-tag:11px;--ks-font-title:26px;--ks-font-section:15px;--ks-dialog-shadow:0 8px 30px #00000024;--ks-action-bg:var(--ks-accent);--ks-action-text:var(--ks-paper);width:min(100%,390px);min-height:776px;box-sizing:border-box;background:var(--ks-bg);color:var(--ks-ink);border:1px solid var(--ks-line);border-radius:30px;box-shadow:0 15px 45px #00000010;font-family:"Segoe UI","Microsoft YaHei",sans-serif;font-size:14px;line-height:1.55;display:flex;flex-direction:column;position:relative;overflow-wrap:anywhere;color-scheme:inherit}
  #kitsune-preview .ks-phone:empty{background:light-dark(#f4f4f4,#202020);border-color:transparent}
  #kitsune-preview .ks-phone *{box-sizing:border-box}
  #kitsune-preview .ks-phone :where(button,input,select,textarea){font:inherit;color:inherit}
  #kitsune-preview .ks-phone button{cursor:var(--cursor-interaction,pointer)}
  #kitsune-preview .ks-phone button:disabled{opacity:.45;cursor:default}
  #kitsune-preview .ks-phone :where(button,input,select,textarea):focus-visible{outline:2px solid var(--ks-accent);outline-offset:2px}
  #kitsune-preview .ks-status{display:flex;justify-content:space-between;align-items:center;padding:16px 25px 5px;font-size:12px;font-weight:600}
  #kitsune-preview .ks-signal{display:flex;align-items:center;gap:5px}
  #kitsune-preview .ks-signal svg{width:13px;height:13px}
  #kitsune-preview .ks-top{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:var(--ks-space-4) var(--ks-space-4) var(--ks-space-3)}
  #kitsune-preview .ks-title{margin:2px 0 0;font-weight:600;line-height:1.3;font-size:var(--ks-font-title);letter-spacing:-.5px}
  #kitsune-preview .ks-sub{font-size:12px;color:var(--ks-dim);margin:6px 0 0}
  #kitsune-preview .ks-icon-btn{display:inline-flex;align-items:center;justify-content:center;width:44px;min-width:44px;min-height:44px;border-radius:var(--ks-control-radius);padding:var(--ks-space-2);border:var(--ks-card-border);background:var(--ks-card-surface);flex-shrink:0}
  #kitsune-preview .ks-icon-btn svg{width:var(--ks-icon-size);height:var(--ks-icon-size)}
  #kitsune-preview .ks-main{flex:1;padding:0 var(--ks-space-4) var(--ks-space-4)}
  #kitsune-preview .ks-row{display:flex;justify-content:space-between;align-items:center;gap:var(--ks-space-2)}
  #kitsune-preview .ks-section{margin-top:var(--ks-space-6);margin-bottom:var(--ks-space-2)}
  #kitsune-preview .ks-section h3{font-weight:600;margin:0;font-size:var(--ks-font-section)}
  #kitsune-preview .ks-note{color:var(--ks-dim);font-size:var(--ks-font-note)}
  #kitsune-preview .ks-text-btn{border:0;background:transparent;color:var(--ks-accent);padding:8px 0;display:inline-flex;align-items:center;gap:4px;font-size:12px;min-height:44px}
  #kitsune-preview .ks-text-btn svg{width:14px;height:14px}
  #kitsune-preview .ks-week{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:1px;margin:10px 0 19px;padding:4px;border:var(--ks-card-border);border-radius:var(--ks-radius);background:var(--ks-card-surface)}
  #kitsune-preview .ks-day{display:flex;flex-direction:column;align-items:center;background:transparent;min-width:0;border:1px solid transparent;border-radius:var(--ks-control-radius);gap:var(--ks-space-1);min-height:44px;padding:var(--ks-space-2) 0;font-size:var(--ks-font-body)}
  #kitsune-preview .ks-day small{font-size:11px;color:var(--ks-dim)}
  #kitsune-preview .ks-day[aria-pressed=true]{background:var(--ks-accent-bg);border-color:var(--ks-accent);color:var(--ks-accent)}
  #kitsune-preview .ks-day[aria-pressed=true] small{color:inherit;opacity:1}
  #kitsune-preview .ks-record-list{display:grid;grid-template-columns:minmax(0,1fr);gap:8px}
  #kitsune-preview .ks-record{display:flex;align-items:center;min-height:70px;min-width:0;width:100%;text-align:left;border:var(--ks-card-border);background:var(--ks-card-surface);border-radius:var(--ks-radius);padding:var(--ks-space-3);gap:var(--ks-space-3)}
  #kitsune-preview .ks-record:hover{background:color-mix(in srgb,var(--ks-paper) 78%,var(--ks-accent-bg))}
  #kitsune-preview .ks-record-icon{width:36px;height:36px;display:flex;align-items:center;justify-content:center;flex-shrink:0;border-radius:var(--ks-control-radius);background:var(--ks-soft);color:var(--ks-dim)}
  #kitsune-preview .ks-record-icon svg{width:var(--ks-icon-size);height:var(--ks-icon-size)}
  #kitsune-preview .ks-record-copy{flex:1;min-width:0}
  #kitsune-preview .ks-record-title{display:flex;align-items:center;gap:6px;flex-wrap:wrap;font-weight:600;font-size:14px}
  #kitsune-preview .ks-record-meta{display:block;color:var(--ks-dim);font-size:12px;margin-top:3px}
  #kitsune-preview .ks-pill{border:var(--ks-card-border);background:var(--ks-card-surface);color:var(--ks-dim);font-weight:400;font-size:var(--ks-font-tag);padding:2px 6px;border-radius:6px;display:inline-block;vertical-align:middle}

  #kitsune-preview .ks-record>svg{width:14px;height:14px;color:var(--ks-dim);flex-shrink:0}
  #kitsune-preview .ks-record.ks-empty{border-style:dashed;background:transparent}
  #kitsune-preview .ks-record.ks-empty .ks-record-icon{color:var(--ks-accent);background:var(--ks-accent-bg)}
  #kitsune-preview .ks-money-card{background:var(--ks-card-surface);border:var(--ks-card-border);border-radius:var(--ks-radius);padding:var(--ks-space-4)}
  #kitsune-preview .ks-money-caption{font-size:12px;color:var(--ks-accent)}
  #kitsune-preview .ks-amount{font-size:30px;line-height:1.3;font-weight:500;letter-spacing:-1px;font-variant-numeric:tabular-nums;margin:7px 0}
  #kitsune-preview .ks-amount small{font-size:14px;letter-spacing:0;margin-right:5px}
  #kitsune-preview .ks-money-meta{display:flex;gap:15px;flex-wrap:wrap;font-size:11px;color:var(--ks-dim);margin-top:8px}
  #kitsune-preview .ks-primary{display:flex;align-items:center;justify-content:center;border:0;background:var(--ks-action-bg,var(--ks-ink));color:var(--ks-action-text,var(--ks-bg));font-weight:500;min-height:var(--ks-control-height);border-radius:var(--ks-control-radius);padding:var(--ks-space-2) var(--ks-space-4);font-size:var(--ks-font-body);gap:var(--ks-space-2)}
  #kitsune-preview .ks-primary svg{width:var(--ks-icon-size);height:var(--ks-icon-size)}

  #kitsune-preview .ks-nav{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));padding:10px 10px 15px;border-top:1px solid var(--ks-line);background:var(--ks-paper);border-radius:0 0 30px 30px;gap:5px}
  #kitsune-preview .ks-nav button{display:flex;flex-direction:column;align-items:center;gap:4px;background:transparent;border:0;color:var(--ks-dim);font-size:11px;padding:5px 0;min-height:49px}
  #kitsune-preview .ks-nav button span{display:flex;align-items:center;justify-content:center;border-radius:15px;width:49px;height:25px}
  #kitsune-preview .ks-nav button svg{width:var(--ks-icon-size);height:var(--ks-icon-size)}
  #kitsune-preview .ks-nav button[aria-current=page]{color:var(--ks-accent);font-weight:600}
  #kitsune-preview .ks-nav button[aria-current=page] span{background:var(--ks-accent-bg)}
  #kitsune-preview .ks-home-indicator{width:100px;height:4px;border-radius:5px;background:var(--ks-ink);opacity:.6;margin:0 auto 8px}

  #kitsune-preview .ks-form{display:grid;gap:var(--ks-space-4);padding-top:var(--ks-space-2)}
  #kitsune-preview .ks-form :where(label,fieldset){min-width:0}
  #kitsune-preview .ks-form fieldset{border:0;padding:0;margin:0}
  #kitsune-preview .ks-form legend{padding:0}
  #kitsune-preview .ks-form-footer{display:grid;gap:var(--ks-space-2)}
  #kitsune-preview .ks-form-footer .ks-footnote{margin:0}
  #kitsune-preview .ks-error:empty{display:none}
  #kitsune-preview .ks-label{display:block;font-weight:500;font-size:var(--ks-font-body);margin-bottom:var(--ks-space-1)}
  #kitsune-preview .ks-required{color:var(--ks-accent);font-size:11px;margin-left:5px;font-weight:400}
  #kitsune-preview .ks-choices{display:flex;gap:8px;flex-wrap:wrap}
  #kitsune-preview .ks-choice{min-height:var(--ks-control-height);border-radius:var(--ks-control-radius);border:var(--ks-card-border);background:var(--ks-card-surface);padding:var(--ks-space-2) var(--ks-space-3);font-size:var(--ks-font-body)}
  #kitsune-preview .ks-choice[aria-pressed=true]{background:var(--ks-accent-bg);border-color:var(--ks-accent);color:var(--ks-accent)}
  #kitsune-preview .ks-control{display:block;width:100%;min-width:0;resize:vertical;min-height:var(--ks-control-height);border:var(--ks-card-border);border-radius:var(--ks-control-radius);background:var(--ks-card-surface);padding:var(--ks-space-2) var(--ks-space-3);font-size:var(--ks-font-body)}
  #kitsune-preview .ks-field-row{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--ks-space-3)}
  #kitsune-preview .ks-error{font-size:12px;color:var(--ks-danger);min-height:0;margin:0}
  #kitsune-preview .ks-success{font-size:12px;color:var(--ks-good);margin:8px 0 0}
  #kitsune-preview .ks-segments{display:flex;border-bottom:1px solid var(--ks-line);gap:23px;margin-bottom:20px}
  #kitsune-preview .ks-segments button{border:0;border-bottom:2px solid transparent;background:transparent;padding:12px 0;color:var(--ks-dim);font-size:14px;min-height:44px}
  #kitsune-preview .ks-segments button[aria-pressed=true]{color:var(--ks-ink);border-bottom-color:var(--ks-ink);font-weight:600}
  #kitsune-preview .ks-stats-panel{border:var(--ks-card-border);border-radius:var(--ks-radius);background:var(--ks-card-surface);padding:var(--ks-space-1) var(--ks-space-4)}
  #kitsune-preview .ks-summary{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--ks-space-2);margin:0 0 var(--ks-space-2)}
  #kitsune-preview .ks-summary>div{border:var(--ks-card-border);border-radius:var(--ks-radius);padding:var(--ks-space-3);background:var(--ks-card-surface)}
  #kitsune-preview .ks-summary strong{display:block;margin-top:3px;font-size:20px;font-weight:500;font-variant-numeric:tabular-nums}
  #kitsune-preview .ks-settings{border:var(--ks-card-border);border-radius:var(--ks-radius);background:var(--ks-card-surface);padding:0 var(--ks-space-4);margin:var(--ks-space-3) 0 var(--ks-space-4)}
  #kitsune-preview .ks-setting{display:flex;align-items:center;min-height:55px;border-bottom:1px solid var(--ks-line);padding:var(--ks-space-2) 0;gap:var(--ks-space-2)}
  #kitsune-preview .ks-setting:last-child{border-bottom:0}
  #kitsune-preview .ks-setting>svg{color:var(--ks-dim);width:var(--ks-icon-size);height:var(--ks-icon-size)}
  #kitsune-preview .ks-setting>span{flex:1;font-size:var(--ks-font-body);min-width:0}
  #kitsune-preview .ks-setting>small{color:var(--ks-dim);font-size:var(--ks-font-note);text-align:right}
  #kitsune-preview .ks-setting select{background:var(--ks-card-surface);border:var(--ks-card-border);border-radius:var(--ks-control-radius);font-size:var(--ks-font-note);min-height:var(--ks-control-height);max-width:48%;min-width:0;padding:var(--ks-space-2);color:var(--ks-ink)}

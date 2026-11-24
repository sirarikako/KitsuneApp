  #kitsune-preview :is(.ks-wallet-dialog,.ks-period-dialog) h3{font-size:var(--ks-font-title);font-weight:600;line-height:1.3;margin:0}
  #kitsune-preview :is(.ks-wallet-body,.ks-period-body){display:grid;gap:var(--ks-space-2);overflow:auto;min-height:0}
  #kitsune-preview .ks-wallet-body p{margin:0}
  #kitsune-preview :is(.ks-wallet-dialog,.ks-period-dialog) .ks-choice{padding:var(--ks-space-2)}
  @media(max-width:400px){#kitsune-preview :is(.ks-wallet-dialog,.ks-period-dialog){padding-inline:var(--ks-space-4)}#kitsune-preview :is(.ks-wallet-dialog,.ks-period-dialog) .ks-choice{padding-inline:var(--ks-space-1)}}
  #kitsune-preview :is(.ks-wallet-dialog,.ks-period-dialog) .ks-choice[aria-pressed=true]{box-shadow:inset 0 0 0 1px var(--ks-accent)}
  #kitsune-preview :is(.ks-wallet-actions,.ks-period-actions){display:flex;justify-content:flex-end;flex-wrap:wrap;gap:var(--ks-space-2);padding-top:var(--ks-space-2)}
  #kitsune-preview :is(.ks-wallet-actions,.ks-period-actions) .ks-text-btn{font-size:var(--ks-font-body);padding:var(--ks-space-2)}
  #kitsune-preview .ks-period-modes{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--ks-space-2)}
  #kitsune-preview .ks-period-months{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:var(--ks-space-2)}
  #kitsune-preview .ks-period-body .ks-error:empty{display:none}
  #kitsune-preview [data-filter-group]{min-width:0;border:0;margin:0;padding:0}
  #kitsune-preview .ks-ledger-filter-options{display:flex;flex-wrap:wrap;gap:var(--ks-space-1)}
  #kitsune-preview [data-filter-group=categoryNames] .ks-ledger-filter-options{display:grid;max-height:216px;overflow:auto}
  #kitsune-preview .ks-ledger-check{display:flex;align-items:center;justify-content:flex-start;gap:var(--ks-space-2);overflow-wrap:anywhere}
  #kitsune-preview .ks-ledger-check input{width:18px;height:18px;accent-color:var(--ks-accent);flex:none}
  #kitsune-preview .ks-ledger-check:has(input:checked){color:var(--ks-accent);border-color:var(--ks-accent);background:var(--ks-accent-bg)}
  #kitsune-preview .ks-ledger-filter-bulk{display:flex;gap:var(--ks-space-2)}
  #kitsune-preview .ks-date-trigger{display:flex;justify-content:space-between;align-items:center;gap:var(--ks-space-2);text-align:left}
  #kitsune-preview .ks-entry-date-time{grid-template-columns:minmax(0,1.6fr) minmax(0,1fr)}
  #kitsune-preview .ks-time-dialog{max-width:280px}
  #kitsune-preview .ks-time-clock{max-width:320px}
  #kitsune-preview .ks-time-fields{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);gap:6px}
  #kitsune-preview .ks-time-fields label{display:grid;gap:4px;min-width:0}
  #kitsune-preview .ks-time-digits,#kitsune-preview .ks-time-colon{font:400 54px/80px system-ui,sans-serif;font-variant-numeric:tabular-nums;text-align:center}
  #kitsune-preview .ks-time-digits{width:100%;min-width:0;height:80px;padding:0;border:0;border-radius:var(--ks-control-radius);background:var(--ks-soft);color:var(--ks-ink)}
  #kitsune-preview .ks-time-digits:is(:focus,[aria-pressed=true]){background:var(--ks-accent-bg);color:var(--ks-accent)}
  #kitsune-preview .ks-time-digits:focus{outline:2px solid var(--ks-accent);outline-offset:-2px}
  #kitsune-preview .ks-time-digits[aria-invalid=true]{color:var(--ks-danger);box-shadow:inset 0 0 0 2px var(--ks-danger)}
  #kitsune-preview .ks-time-dialog .ks-icon-btn{margin-inline-end:auto;border:0;background:transparent}
  #kitsune-preview .ks-time-face{position:relative;align-self:center;width:min(100%,256px);aspect-ratio:1;border-radius:50%;background:var(--ks-soft)}
  #kitsune-preview .ks-time-face button{position:absolute;transform:translate(-50%,-50%);display:grid;place-items:center;width:16%;aspect-ratio:1;padding:0;border:0;border-radius:50%;background:transparent}
  #kitsune-preview .ks-time-face button[aria-pressed=true]{background:var(--ks-accent);color:var(--ks-paper)}
  #kitsune-preview .ks-time-hand{position:absolute;bottom:50%;left:calc(50% - 1px);width:2px;background:var(--ks-accent);transform-origin:center bottom}
  #kitsune-preview .ks-time-hand::after{content:'';position:absolute;bottom:-3px;left:-2px;width:6px;height:6px;border-radius:50%;background:inherit}
  #kitsune-preview .ks-calendar-dialog{max-width:360px;gap:var(--ks-space-2)}
  #kitsune-preview .ks-calendar-dialog h3{font-size:20px}
  #kitsune-preview .ks-calendar-dialog .ks-period-body{padding:2px}
  #kitsune-preview .ks-calendar-mode{justify-self:end}
  #kitsune-preview .ks-calendar-selected{font-size:16px;font-weight:500}
  #kitsune-preview .ks-calendar-month{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:var(--ks-space-2)}
  #kitsune-preview .ks-calendar-month .ks-choice{display:flex;align-items:center;justify-content:center;gap:var(--ks-space-2)}
  #kitsune-preview .ks-calendar-grid{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));text-align:center}
  #kitsune-preview .ks-calendar-day,#kitsune-preview .ks-calendar-grid span[aria-hidden]{min-height:42px}
  #kitsune-preview .ks-calendar-day{border:1px solid transparent;border-radius:50%;background:transparent;color:var(--ks-ink);font:inherit}
  #kitsune-preview .ks-calendar-day[aria-current=date]{border-color:var(--ks-accent);color:var(--ks-accent)}
  #kitsune-preview .ks-calendar-day[aria-pressed=true]{background:var(--ks-action-bg);color:var(--ks-action-text)}
  #kitsune-preview .ks-calendar-month .ks-icon-btn:disabled{opacity:.4}
  #kitsune-preview .ks-wallet-check{display:flex;align-items:center;gap:var(--ks-space-2);min-height:48px;font-size:var(--ks-font-body)}
  #kitsune-preview .ks-wallet-check input{width:18px;height:18px;accent-color:var(--ks-accent);flex-shrink:0}
  #kitsune-preview .ks-wallet-setting{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:12px;padding-top:12px;border-top:1px solid var(--ks-line);font-size:13px}
  #kitsune-preview .ks-wallet-setting small{display:block;color:var(--ks-dim);font-size:11px;margin-top:3px}
  #kitsune-preview .ks-wallet-setting input{width:19px;height:19px;accent-color:var(--ks-accent);flex-shrink:0}
  #kitsune-preview .ks-wallet-setting+.ks-primary{margin-top:var(--ks-space-3)}
  #kitsune-preview .ks-setting-link{width:100%;font:inherit;color:var(--ks-ink);background:transparent;text-align:left;border:0;cursor:pointer}
  #kitsune-preview .ks-setting>.ks-backup-warning{color:var(--ks-danger);white-space:normal}
  #kitsune-preview .ks-setting-link>svg:last-child{flex-shrink:0}
  #kitsune-preview .ks-wallet-cards{display:grid;gap:var(--ks-space-2)}
  #kitsune-preview .ks-wallet-cards .ks-money-card{padding:var(--ks-space-3)}
  #kitsune-preview .ks-wallet-card-heading{display:flex;align-items:center;justify-content:space-between;gap:var(--ks-space-1)}
  #kitsune-preview .ks-wallet-card-label{display:flex;flex-wrap:wrap;align-items:center;gap:var(--ks-space-1) 6px;min-width:0;overflow-wrap:anywhere}
  #kitsune-preview .ks-wallet-card-actions{display:flex;gap:var(--ks-space-1)}
  #kitsune-preview .ks-wallet-cards .ks-amount{margin:2px 0}
  #kitsune-preview .ks-category-manager{display:flex;flex-direction:column;gap:var(--ks-space-3)}
  #kitsune-preview .ks-category-manager .ks-category-rows{background:var(--ks-card-surface);border:var(--ks-card-border);border-radius:var(--ks-radius);padding:0 var(--ks-space-3)}
  #kitsune-preview .ks-category-manager .ks-setting{gap:var(--ks-space-1)}
  #kitsune-preview .ks-category-rows .ks-setting-link{border-bottom:var(--ks-card-border)}
  #kitsune-preview .ks-category-rows .ks-setting-link svg{margin-left:auto}
  #kitsune-preview .ks-backup-status{font-size:20px;font-weight:500;margin:0}
  #kitsune-preview .ks-backup-status~p{margin:var(--ks-space-1) 0 0}
  #kitsune-preview .ks-category-form{margin-top:4px}
  #kitsune-preview .ks-category-form .ks-form-actions{align-items:center}
  #kitsune-preview .ks-wallet-check>span{flex:1}
  #kitsune-preview .ks-wallet-check small{font-size:var(--ks-font-note);color:var(--ks-accent)}
  #kitsune-preview .ks-filter-chips{display:flex;flex-wrap:wrap;gap:6px;margin:0 0 15px}
  #kitsune-preview .ks-filter-chip{border:var(--ks-card-border);background:var(--ks-card-surface);font-size:var(--ks-font-tag);padding:2px 6px;border-radius:6px;color:var(--ks-dim)}
  #kitsune-preview .ks-record-ellipsis{display:block;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  #kitsune-preview .ks-preparation-choices{display:flex;gap:8px;flex-wrap:wrap}
  #kitsune-preview .ks-preparation-check{display:flex;align-items:center;gap:7px;min-height:var(--ks-control-height);padding:var(--ks-space-2) var(--ks-space-3);border:var(--ks-card-border);border-radius:var(--ks-control-radius);background:var(--ks-card-surface);font-size:var(--ks-font-body)}
  #kitsune-preview .ks-preparation-check input{width:16px;height:16px;accent-color:var(--ks-accent)}
  #kitsune-preview .ks-form-actions{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--ks-space-2)}
  #kitsune-preview .ks-appearance-note{font-size:12px;color:var(--ks-dim);line-height:1.65;margin:0 0 14px}
  #kitsune-preview .ks-appearance-preview{border:var(--ks-card-border);border-radius:var(--ks-radius);color:var(--ks-ink);padding:var(--ks-space-3);background:var(--ks-bg);margin-bottom:var(--ks-space-3)}
  #kitsune-preview .ks-appearance-preview .ks-row{font-size:var(--ks-font-body)}
  #kitsune-preview .ks-preview-modes{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:10px}
  #kitsune-preview .ks-preview-modes .ks-choice{min-height:var(--ks-control-height);padding:var(--ks-space-2) var(--ks-space-3)}
  #kitsune-preview .ks-appearance-actions{display:grid;grid-template-columns:1fr 1fr;margin:12px 0 20px;gap:var(--ks-space-2)}
  #kitsune-preview .ks-custom-color-row{display:flex;align-items:center;gap:var(--ks-space-2);padding:var(--ks-space-3) 0;border-bottom:0}
  #kitsune-preview .ks-custom-color-row input[type=color]{width:45px;padding:3px;flex-shrink:0;min-height:var(--ks-control-height);background:var(--ks-card-surface);border:var(--ks-card-border);border-radius:var(--ks-control-radius);font-size:var(--ks-font-body)}
  #kitsune-preview .ks-custom-color-row input[type=text]{min-width:0;flex:1;width:100%;padding:10px;min-height:var(--ks-control-height);background:var(--ks-card-surface);border:var(--ks-card-border);border-radius:var(--ks-control-radius);font-size:var(--ks-font-body)}
  #kitsune-preview .ks-color-success{color:var(--ks-good);font-size:12px;margin:0 0 12px}
  #kitsune-preview .ks-footnote{text-align:center;font-size:11px;color:var(--ks-dim);margin:18px 0 0}
  #kitsune-preview .ks-bar-track{height:8px;background:var(--ks-soft);border-radius:4px;margin-top:8px}
  #kitsune-preview .ks-bar-fill{height:100%;background:var(--ks-accent);border-radius:4px}
  #kitsune-preview .ks-bar-income .ks-bar-fill{background:var(--ks-good)}
  #kitsune-preview .ks-bar-expense .ks-bar-fill{background:var(--ks-expense)}
  @media(max-width:400px){#kitsune-preview .ks-stage{padding:12px 0;min-height:855px}#kitsune-preview .ks-main,#kitsune-preview .ks-top{padding-left:var(--ks-space-3);padding-right:var(--ks-space-3)}#kitsune-preview .ks-choice{padding-left:var(--ks-space-2);padding-right:var(--ks-space-2)}}
  @media(pointer:coarse){#kitsune-preview .ks-text-btn{min-height:44px}#kitsune-preview .ks-control{font-size:16px}}
  #kitsune-preview .ks-preparation-check:has(input:checked){background:var(--ks-accent-bg);border-color:var(--ks-accent);color:var(--ks-accent)}
  #kitsune-preview .ks-detail-list>div:last-child{border-bottom:0}
  #kitsune-preview .ks-appearance-preview .ks-record{cursor:default}
  #kitsune-preview .ks-appearance-preview .ks-record:hover{background:var(--ks-card-surface)}
  #kitsune-preview .ks-appearance-preview .ks-primary{margin-top:var(--ks-space-2)}
  #kitsune-preview .ks-phone{overflow:clip}
  #kitsune-preview .ks-page-motion{display:flex;flex:1;flex-direction:column;min-width:0;background:var(--ks-bg)}
  #kitsune-preview .ks-motion-copy{position:absolute;inset:0;z-index:8;pointer-events:none;overflow:hidden;border-radius:30px}
  #kitsune-preview .ks-motion-copy *{pointer-events:none!important}
  #kitsune-preview .ks-motion-modal-copy{z-index:21}
  #kitsune-preview .ks-motion-appearance-copy{pointer-events:none;overflow:hidden}
  #kitsune-preview .ks-phone:has(>.ks-ledger-screen){height:clamp(570px,92vh,740px);min-height:0}
  #kitsune-preview .ks-ledger-screen{position:relative;min-height:0;overflow:hidden}
  #kitsune-preview .ks-ledger-screen .ks-main{min-height:0;overflow-y:auto;overflow-anchor:none;overscroll-behavior:contain;padding-top:12px}
  #kitsune-preview .ks-ledger-with-fab .ks-main{padding-bottom:84px}
  #kitsune-preview .ks-ledger-screen .ks-segments{flex:none;margin:0 var(--ks-space-4);gap:clamp(8px,5vw,20px)}
  #kitsune-preview .ks-ledger-collapsible{flex:none;overflow:hidden;display:flex;flex-direction:column;justify-content:flex-end}
  #kitsune-preview .ks-ledger-collapsible>*{flex:none}
  #kitsune-preview .ks-ledger-filters{padding:8px var(--ks-space-4) 0;display:grid;gap:8px}
  #kitsune-preview .ks-ledger-filter-row{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
  #kitsune-preview .ks-ledger-filter-row.ks-ledger-custom{grid-template-columns:minmax(0,1fr)}
  #kitsune-preview .ks-ledger-filter{min-width:0;display:flex;justify-content:space-between;align-items:center;gap:6px;text-align:left}
  #kitsune-preview .ks-ledger-filter svg{flex:none;width:14px;height:14px;color:var(--ks-accent)}
  #kitsune-preview .ks-ledger-wallet span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  #kitsune-preview .ks-ledger-list-actions{display:flex;align-items:center;gap:8px}
  #kitsune-preview .ks-ledger-list-actions .ks-choice{display:flex;align-items:center;gap:6px}
  #kitsune-preview .ks-ledger-list-actions svg{width:18px;height:18px}
  #kitsune-preview .ks-ledger-fab{position:absolute;z-index:1;inset:auto 16px 16px auto;border-radius:999px;min-height:56px;min-width:56px;justify-content:center;gap:0;padding:8px 16px;box-shadow:0 4px 10px #0003}
  #kitsune-preview .ks-ledger-fab span{display:block;overflow:hidden;white-space:nowrap;max-width:7em;padding-left:8px;transition:max-width 180ms ease,padding-left 180ms ease,opacity 140ms ease}
  #kitsune-preview .ks-ledger-fab.ks-fab-compact span{max-width:0;padding-left:0;opacity:0}
  @media(prefers-reduced-motion:reduce){#kitsune-preview .ks-ledger-fab span{transition:none}}
</style>

@import org.fuin.porelo.release.PlanTemplate
@param PlanTemplate page
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Release ${page.release()}</title>
<style>
:root{--surface:#fcfcfb;--panel:#ffffff;--ink:#0b0b0b;--ink2:#52514e;--muted:#8a8984;--grid:#e6e5e1;
--done:#0ca30c;--review:#2a78d6;--impl:#fab219;--backlog:#8ac8f0;--estimated:#9575b5;--analyzed:#77766f;--accepted:#b9b8b2;--idea:#eb6834;--new:#ffffff;}
@media (prefers-color-scheme:dark){:root{--surface:#1a1a19;--panel:#222220;--ink:#f0efec;--ink2:#c3c2b7;--muted:#8f8e88;--grid:#383835;--backlog:#80bee6;--estimated:#b497cf;--analyzed:#77766f;--accepted:#aaa9a2;--review:#3987e5;--idea:#d95926;--new:#1a1a19;}}
body{margin:0;background:var(--surface);color:var(--ink);font:14px/1.45 system-ui,sans-serif}
main{max-width:980px;margin:0 auto;padding:24px 16px}
h1{font-size:22px;margin:0 0 4px}h2{font-size:17px;margin:30px 0 4px}h2.section{font-size:20px}h3{font-size:14px;margin:14px 0 4px}
p.sub,.note{color:var(--ink2)}p.sub{margin:0 0 16px;font-size:16px;color:var(--ink)}
.legend{display:flex;gap:14px;color:var(--ink2);font-size:13px;margin:6px 0 10px;flex-wrap:wrap}
.legend i{display:inline-block;width:12px;height:12px;border-radius:3px;margin-right:5px;vertical-align:-1px;border:1px solid var(--grid)}
.row{display:grid;grid-template-columns:230px 1fr 70px;gap:10px;align-items:center;margin:5px 0}
.row .lab{font-size:13px}.row .n{color:var(--ink2);font-size:12px}
.bar{display:flex;height:20px;border-radius:4px;overflow:hidden;background:var(--grid)}
.bar span{display:block;height:100%;border-right:1px solid var(--surface)}
.sprint{background:var(--panel);border:1px solid var(--grid);border-radius:10px;padding:14px 16px;margin:14px 0}
.sprint .goal{color:var(--ink2);margin:2px 0 8px}
.fn{border-top:1px solid var(--grid);padding:8px 0 4px}
.fn b{font-size:13.5px}.fn .how{color:var(--ink2);font-size:13px;margin:3px 0}
.chip{display:inline-block;font-size:12px;padding:1px 7px;border-radius:10px;margin:2px 3px 2px 0;border:1px solid var(--grid)}
.chip.done{background:var(--done);color:#fff;border-color:var(--done)}.chip.review{background:var(--review);color:#fff;border-color:var(--review)}
.chip.impl{background:var(--impl);color:#0b0b0b;border-color:var(--impl)}.chip.backlog{background:var(--backlog);color:#0b0b0b;border-color:var(--backlog)}
.chip.estimated{background:var(--estimated);color:#0b0b0b;border-color:var(--estimated)}.chip.analyzed{background:var(--analyzed);color:#fff;border-color:var(--analyzed)}
.chip.accepted{background:var(--accepted);color:#0b0b0b;border-color:var(--accepted)}
.chip.idea{background:var(--idea);color:#fff;border-color:var(--idea)}.chip.new{border-style:dashed;color:var(--ink2)}
@media (max-width:600px){.row{grid-template-columns:1fr;gap:2px}}

a.chip{text-decoration:none} .row>svg{width:100%;height:20px;display:block} .ic{white-space:nowrap} .stand{color:var(--ink2);font-size:12px} .fn p{white-space:pre-wrap}


.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip-path:inset(50%);white-space:nowrap;border:0}
</style></head><body><main>
<h1>Release ${page.release()}</h1>
<p class="sub">${page.goal()}</p>
<p class="stand"><em>Stand: ${page.stand()}</em></p>
<h2 class="section">Sprint progress</h2>
<div class="legend">
@for(var state : PlanTemplate.STATES)
<span><i style="background:var(--${state.color()})"></i>${state.label()}</span>
@endfor
</div>
<div id="bars">
@for(var sprint : page.sprints())
<div class="row"><span class="lab">${sprint.id()}. ${sprint.title()}</span>${sprint.image()}<span class="ic">
@for(var function : sprint.functions())
<span title="${function.title()}: ${function.tested().isEmpty() ? "Not tested" : "Tested " + function.tested()}">${function.tested().isEmpty() ? "⚪" : "✅"}</span>
@endfor
</span></div>
@endfor
</div>
<p class="note">${PlanTemplate.INTRODUCTION}</p>
<div id="sprints">
<h2 class="section">Issues Details</h2>
@for(var sprint : page.sprints())
<div class="sprint"><h2 style="margin-top:0">Sprint ${sprint.id()}: ${sprint.title()}</h2><p class="goal">${sprint.goal()}</p>
@for(var function : sprint.functions())
<div class="fn"><b>${function.title()} ${function.tested().isEmpty() ? "⚪ Not tested" : "✅ Tested " + function.tested()}</b>
<p class="how">Test: ${function.test()}</p>
@for(var chip : function.chips())
@template.chip(chip)
@endfor
@if(!function.covers().isEmpty())
<div class="how">covers @for(int i = 0; i < function.covers().size(); i++)!{var parent = function.covers().get(i);}@if(i > 0), @endif@if(parent.link().isEmpty())${parent.number()}@else<a href="${parent.link()}" title="${parent.detail()}">${parent.number()}</a>@endif · ${parent.state()}@endfor</div>
@endif
</div>
@endfor
</div>
@endfor
</div>
</main></body></html>

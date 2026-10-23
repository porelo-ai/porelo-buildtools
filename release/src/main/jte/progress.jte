@import org.fuin.porelo.release.PlanTemplate
@param PlanTemplate page
!{int labelWidth = Math.max(230, page.sprints().stream().mapToInt(s -> (s.id() + ". " + s.title()).length() * 8).max().orElse(230));}
!{int marksWidth = Math.max(100, page.sprints().stream().mapToInt(s -> s.functions().size() * 24).max().orElse(100));}
!{int width = Math.max(980, labelWidth + 550 + marksWidth);}
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${80 + page.sprints().size() * 30}" role="img" aria-label="Sprint progress for release ${page.release()}">
<title>Sprint progress for release ${page.release()}</title>
<style>
text{fill:#0b0b0b;font:13px system-ui,sans-serif}.legend{fill:#52514e}
@media (prefers-color-scheme:dark){text{fill:#f0efec}.legend{fill:#c3c2b7}.c-surface{fill:#1a1a19}.c-grid{fill:#383835}.c-open{fill:#5e5d58}.c-review{fill:#3987e5}.c-dec{fill:#d95926}.c-new{fill:#1a1a19}}
</style>
<rect width="100%" height="100%" fill="#fcfcfb" class="c-surface"/>
@for(int i = 0; i < PlanTemplate.STATES.size(); i++)
!{var state = PlanTemplate.STATES.get(i);}
<g transform="translate(${(i % 3) * 320} ${(i / 3) * 24})">
<rect x="1" y="5" width="12" height="12" rx="3" fill="${PlanTemplate.hex(state.color())}" class="c-${state.color()}" stroke="#e6e5e1"/>
<text class="legend" x="19" y="16">${state.label()}</text>
</g>
@endfor
@for(int i = 0; i < page.sprints().size(); i++)
!{var sprint = page.sprints().get(i);}
<g transform="translate(0 ${70 + i * 30})">
<text x="0" y="17">${sprint.id()}. ${sprint.title()}</text>
<desc>${sprint.counts()}. @for(var function : sprint.functions())@for(var chip : function.chips())${chip.text()}: ${chip.detail()}; @endfor@endfor</desc>
<svg x="${labelWidth}" width="540" height="24" viewBox="0 0 540 24">
<svg x="0" y="2" width="400" height="20" viewBox="0 0 400 20">
<rect width="400" height="20" rx="4" fill="#e6e5e1" class="c-grid"/>
@for(var segment : sprint.segments())
<rect x="${segment.x()}" width="${segment.width()}" height="20" fill="${PlanTemplate.hex(segment.color())}" class="c-${segment.color()}" stroke="#fcfcfb"><title>${segment.label()}: ${segment.count()}</title></rect>
@endfor
</svg>
<text class="legend" x="410" y="17">${sprint.done()} of ${sprint.total()} done</text>
</svg>
@for(int j = 0; j < sprint.functions().size(); j++)
!{var function = sprint.functions().get(j);}
<text x="${labelWidth + 550 + j * 24}" y="17"><title>${function.title()}: ${function.tested().isEmpty() ? "Not tested" : "Tested " + function.tested()}</title>${function.tested().isEmpty() ? "⚪" : "✅"}</text>
@endfor
</g>
@endfor
</svg>

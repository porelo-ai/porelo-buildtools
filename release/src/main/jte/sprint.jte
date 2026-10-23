@import org.fuin.porelo.release.PlanTemplate
@param PlanTemplate.Sprint sprint
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 540 24" role="img" aria-label="Sprint ${sprint.id()}: ${sprint.done()} of ${sprint.total()} done">
<title>Sprint ${sprint.id()}: ${sprint.title()}</title>
<desc>${sprint.done()} of ${sprint.total()} done. @for(var segment : sprint.segments())${segment.label()}: ${segment.count()}; @endfor@for(var function : sprint.functions())@for(var chip : function.chips())${chip.text()}: ${chip.detail()}; @endfor@endfor</desc>
<style>text{fill:var(--ink2);font:12px system-ui,sans-serif}rect{stroke:var(--surface);stroke-width:1}
</style>
<svg x="0" y="2" width="400" height="20" viewBox="0 0 400 20">
<rect width="400" height="20" rx="4" fill="var(--grid)"/>
@for(var segment : sprint.segments())
<rect x="${segment.x()}" width="${segment.width()}" height="20" fill="var(--${segment.color()})"><title>${segment.label()}: ${segment.count()}</title></rect>
@endfor
</svg>
<text x="410" y="17">${sprint.done()} of ${sprint.total()} done</text>
</svg>

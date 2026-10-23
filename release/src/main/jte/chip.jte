@import org.fuin.porelo.release.PlanTemplate
@param PlanTemplate.Chip chip
@if(chip.link().isEmpty())
<span class="chip ${chip.color()}" title="${chip.detail()}">${chip.text()}<span class="sr-only"> · ${chip.detail()}</span></span>
@else
<a class="chip ${chip.color()}" href="${chip.link()}" title="${chip.detail()}">${chip.text()}<span class="sr-only"> · ${chip.detail()}</span></a>
@endif

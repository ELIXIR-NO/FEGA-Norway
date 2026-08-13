---
title: Running the e2e distributions
description: "Pick one of the six end-to-end distributions: two runners across three target environments."
---

Two e2e runners, three target environments, six distributions. Both runners are measured against
the same docker compose stack, so a difference in result is a difference between the suites and
nothing else. Pick the one you mean to run.

<p class="fega-tiles__label">Go runner <span>(the default, one binary per environment)</span></p>

<div class="fega-tiles">
	<div class="fega-card fega-tile">
		<span class="fega-card__eyebrow">Mocked stack</span>
		<span class="fega-card__title fega-tile__title">
			<a class="fega-tile__link" href="../e2e/go-fega/">e2e-fega</a>
		</span>
		<span class="fega-card__body fega-tile__body">
			Nine stages against the local compose stack. What CI runs on every push, and what
			<code>./dev.sh start</code> gives you with no setup at all.
		</span>
	</div>
	<div class="fega-card fega-tile">
		<span class="fega-card__eyebrow">Live staging</span>
		<span class="fega-card__title fega-tile__title">
			<a class="fega-tile__link" href="../e2e/go-egadev/">e2e-egadev</a>
		</span>
		<span class="fega-card__body fega-tile__body">
			Six stages against <code>egadev.uio.no</code>. Runs on a host as well as in the
			container, and needs real credentials and key paths.
		</span>
	</div>
	<div class="fega-card fega-tile">
		<span class="fega-card__eyebrow">Not implemented</span>
		<span class="fega-card__title fega-tile__title">
			<a class="fega-tile__link" href="../e2e/go-gdi/">e2e-gdi</a>
		</span>
		<span class="fega-card__body fega-tile__body">
			A placeholder that exits 1 on purpose, so a GDI run can never be mistaken for a pass.
		</span>
	</div>
</div>

<p class="fega-tiles__label">JUnit runner <span>(retiring, one test class per environment)</span></p>

<div class="fega-tiles">
	<div class="fega-card fega-tile fega-tile--retired">
		<span class="fega-card__eyebrow">Mocked stack</span>
		<span class="fega-card__title fega-tile__title">
			<a class="fega-tile__link" href="../e2e/junit-fega/">FEGA</a>
		</span>
		<span class="fega-card__body fega-tile__body">
			The same nine checks as <code>e2e-fega</code>. The only distribution you can run from an
			IDE, which makes it the one to reach for when debugging a service.
		</span>
		<a class="fega-tile__badge" href="https://github.com/ELIXIR-NO/FEGA-Norway/issues/851">Retiring &middot; #851</a>
	</div>
	<div class="fega-card fega-tile fega-tile--retired">
		<span class="fega-card__eyebrow">Live staging</span>
		<span class="fega-card__title fega-tile__title">
			<a class="fega-tile__link" href="../e2e/junit-egadev/">EGA_DEV</a>
		</span>
		<span class="fega-card__body fega-tile__body">
			The same six checks as <code>e2e-egadev</code>, against the same live environment.
		</span>
		<a class="fega-tile__badge" href="https://github.com/ELIXIR-NO/FEGA-Norway/issues/851">Retiring &middot; #851</a>
	</div>
	<div class="fega-card fega-tile fega-tile--retired">
		<span class="fega-card__eyebrow">Empty</span>
		<span class="fega-card__title fega-tile__title">
			<a class="fega-tile__link" href="../e2e/junit-gdi/">GDI</a>
		</span>
		<span class="fega-card__body fega-tile__body">
			Seven test methods with empty bodies. It asserts nothing and always passes, which is
			worse than the Go placeholder that fails.
		</span>
		<a class="fega-tile__badge" href="https://github.com/ELIXIR-NO/FEGA-Norway/issues/851">Retiring &middot; #851</a>
	</div>
</div>

---
title: Running the e2e distributions
description: "Pick one of the six end-to-end distributions: two runners across three target environments."
---

Two e2e runners, three target environments, six distributions. Both runners are measured against
the same docker compose stack, so a difference in result is a difference between the suites and
nothing else. Pick the one you mean to run.

<p class="fega-tiles__label">Go runner <span>(the default, one binary per environment)</span></p>

<div class="fega-tiles">
	<a class="fega-card fega-tile" href="../e2e/go-fega/">
		<span class="fega-card__eyebrow">Mocked stack</span>
		<span class="fega-card__title fega-tile__title">e2e-fega</span>
		<span class="fega-card__body fega-tile__body">
			Nine stages against the local compose stack. What CI runs on every push, and what
			<code>./dev.sh start</code> gives you with no setup at all.
		</span>
	</a>
	<a class="fega-card fega-tile" href="../e2e/go-egadev/">
		<span class="fega-card__eyebrow">Live staging</span>
		<span class="fega-card__title fega-tile__title">e2e-egadev</span>
		<span class="fega-card__body fega-tile__body">
			Six stages against <code>egadev.uio.no</code>. Runs on a host as well as in the
			container, and needs real credentials and key paths.
		</span>
	</a>
	<a class="fega-card fega-tile" href="../e2e/go-gdi/">
		<span class="fega-card__eyebrow">Not implemented</span>
		<span class="fega-card__title fega-tile__title">e2e-gdi</span>
		<span class="fega-card__body fega-tile__body">
			A placeholder that exits 1 on purpose, so a GDI run can never be mistaken for a pass.
		</span>
	</a>
</div>

<p class="fega-tiles__label">JUnit runner <span>(retiring, one test class per environment)</span></p>

<div class="fega-tiles">
	<a class="fega-card fega-tile fega-tile--retired" href="../e2e/junit-fega/">
		<span class="fega-card__eyebrow">Mocked stack</span>
		<span class="fega-card__title fega-tile__title">FEGA</span>
		<span class="fega-card__body fega-tile__body">
			The same nine checks as <code>e2e-fega</code>. The only distribution you can run from an
			IDE, which makes it the one to reach for when debugging a service.
		</span>
		<span class="fega-tile__badge">Retiring &middot; #851</span>
	</a>
	<a class="fega-card fega-tile fega-tile--retired" href="../e2e/junit-egadev/">
		<span class="fega-card__eyebrow">Live staging</span>
		<span class="fega-card__title fega-tile__title">EGA_DEV</span>
		<span class="fega-card__body fega-tile__body">
			The same six checks as <code>e2e-egadev</code>, against the same live environment.
		</span>
		<span class="fega-tile__badge">Retiring &middot; #851</span>
	</a>
	<a class="fega-card fega-tile fega-tile--retired" href="../e2e/junit-gdi/">
		<span class="fega-card__eyebrow">Empty</span>
		<span class="fega-card__title fega-tile__title">GDI</span>
		<span class="fega-card__body fega-tile__body">
			Seven test methods with empty bodies. It asserts nothing and always passes, which is
			worse than the Go placeholder that fails.
		</span>
		<span class="fega-tile__badge">Retiring &middot; #851</span>
	</a>
</div>

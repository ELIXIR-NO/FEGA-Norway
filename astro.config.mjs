// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import mermaid from 'astro-mermaid';

// Diagrams render client-side, so a page grows after the browser has already
// honoured the URL fragment: every heading below a diagram ends up roughly a
// screen further down than where the reader was left. Re-run the jump once the
// diagrams stop changing the layout. Silent when the page has no diagrams, when
// they never render (nothing moved, so the original jump still holds), or once
// the reader has started scrolling for themselves.
const rejumpAfterDiagrams = `
(function () {
	if (!location.hash) return;

	var jumped = false;
	var cancelled = false;
	var observer = null;
	var timer = 0;

	function stop() {
		if (observer) observer.disconnect();
		if (timer) clearTimeout(timer);
		removeEventListener('wheel', cancel);
		removeEventListener('touchmove', cancel);
		removeEventListener('keydown', cancel);
		removeEventListener('load', jump);
	}

	function cancel() {
		cancelled = true;
		stop();
	}

	function unrendered() {
		return document.querySelectorAll('pre.mermaid:not([data-processed])').length;
	}

	// The browser performs its own fragment scroll at load. Jumping before that
	// would simply be overwritten, so both conditions have to hold.
	function ready() {
		return document.readyState === 'complete' && unrendered() === 0;
	}

	function jump() {
		if (jumped || cancelled || !ready()) return;
		jumped = true;
		stop();
		var target = document.getElementById(decodeURIComponent(location.hash.slice(1)));
		// scrollIntoView honours the scroll-padding-top that clears the sticky header.
		requestAnimationFrame(function () {
			if (!cancelled && target) target.scrollIntoView();
		});
	}

	function watch() {
		if (cancelled || !document.querySelector('pre.mermaid')) return;

		addEventListener('load', jump);
		if (unrendered() === 0) return jump();

		observer = new MutationObserver(jump);
		observer.observe(document.body, {
			subtree: true,
			attributes: true,
			attributeFilter: ['data-processed'],
		});
		timer = setTimeout(stop, 5000);
	}

	addEventListener('wheel', cancel, { passive: true, once: true });
	addEventListener('touchmove', cancel, { passive: true, once: true });
	addEventListener('keydown', cancel, { once: true });

	if (document.readyState === 'loading') {
		addEventListener('DOMContentLoaded', watch);
	} else {
		watch();
	}
})();
`;

// site/base target GitHub Pages for a project repo, which serves under
// /<repo>/. Both must be set or every asset and internal link 404s there
// while still resolving locally.
export default defineConfig({
	site: 'https://elixir-no.github.io',
	base: '/FEGA-Norway',
	integrations: [
		// autoTheme maps Starlight's data-theme straight onto mermaid's built-in
		// 'default'/'dark', which would discard themeVariables and ship the stock
		// lavender palette. Pinning 'base' keeps the variables below authoritative.
		// Nodes carry their own light fill with dark text, so they stay legible on
		// both the light and dark page backgrounds.
		mermaid({
			theme: 'base',
			autoTheme: false,
			mermaidConfig: {
				themeVariables: {
					fontFamily:
						'"Hanken Grotesk Variable", ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
					fontSize: '15px',

					primaryColor: '#d6eaee',
					primaryTextColor: '#0b3a44',
					primaryBorderColor: '#1f7a8c',
					secondaryColor: '#e8f1f2',
					secondaryTextColor: '#0b3a44',
					secondaryBorderColor: '#7fb0bb',
					tertiaryColor: '#f3f8f9',
					tertiaryTextColor: '#0b3a44',
					tertiaryBorderColor: '#b9d3d8',

					lineColor: '#5f8f9a',
					textColor: '#3f6a73',
					mainBkg: '#d6eaee',
					nodeBorder: '#1f7a8c',
					clusterBkg: '#f3f8f9',
					clusterBorder: '#b9d3d8',
					edgeLabelBackground: '#ffffff',

					actorBkg: '#d6eaee',
					actorBorder: '#1f7a8c',
					actorTextColor: '#0b3a44',
					actorLineColor: '#9ab8bf',
					signalColor: '#3f6a73',
					signalTextColor: '#3f6a73',
					labelBoxBkgColor: '#d6eaee',
					labelBoxBorderColor: '#1f7a8c',
					labelTextColor: '#0b3a44',
					loopTextColor: '#3f6a73',
					noteBkgColor: '#fdf6e3',
					noteBorderColor: '#d9c689',
					noteTextColor: '#4a3f1d',
					activationBkgColor: '#bcdde3',
					activationBorderColor: '#1f7a8c',
				},
				flowchart: { curve: 'basis', padding: 16 },
				sequence: { useMaxWidth: true, wrap: true },
			},
		}),
		starlight({
			title: 'FEGA Norway',
			// The mark only, lifted from the official lockup at
			// https://ega.elixir.no/img/EGA_submarcas_Norway.svg. The full lockup carries
			// the "Federated European Genome-phenome Archive / Norway" wordmark, which is
			// unreadable at nav height, so the mark pairs with the title text instead and
			// replacesTitle stays off. Self-hosted like the fonts: nothing is fetched from
			// ega.elixir.no at runtime.
			logo: {
				src: './src/assets/fega-norway.svg',
				alt: 'Federated EGA Norway',
			},
			description:
				'Engineering documentation for the Norwegian Federated EGA node: architecture, local development, contribution rules and operational runbooks.',
			social: [
				{
					icon: 'github',
					label: 'GitHub',
					href: 'https://github.com/ELIXIR-NO/FEGA-Norway',
				},
			],
			editLink: {
				baseUrl: 'https://github.com/ELIXIR-NO/FEGA-Norway/edit/gh-pages/',
			},
			lastUpdated: true,
			head: [{ tag: 'script', content: rejumpAfterDiagrams }],
			customCss: ['./src/styles/theme.css'],
			components: {
				Footer: './src/components/Footer.astro',
				ThemeSelect: './src/components/ThemeSelect.astro',
			},
			sidebar: [
				{
					label: 'Start here',
					items: [
						{ label: 'What this is', slug: 'start/overview' },
						{ label: 'The component map', slug: 'start/components' },
					],
				},
				{
					label: 'Architecture',
					items: [
						{ label: 'System overview', slug: 'architecture/system' },
						{ label: 'Upload', slug: 'architecture/upload' },
						{ label: 'File ingestion', slug: 'architecture/ingestion' },
						{ label: 'Dataset operations', slug: 'architecture/dataset-operations' },
						{ label: 'Cancelling an ingestion', slug: 'architecture/cancel' },
						{ label: 'Export and download', slug: 'architecture/export-download' },
					],
				},
				{
					label: 'Run it locally',
					items: [
						{ label: 'Prerequisites', slug: 'local/prerequisites' },
						{ label: 'The dev.sh workflow', slug: 'local/dev-script' },
						{ label: 'Troubleshooting', slug: 'local/troubleshooting' },
					],
				},
				// Its own section rather than a group nested under "Run it locally": a
				// nested group renders with the same weight as a top-level one, so it read
				// as a peer of the sections around it while sitting a level deeper than
				// anything else in the sidebar. Chooser first, then runner, then target.
				{
					label: 'The e2e suites',
					items: [
						{ label: 'Pick a distribution', slug: 'local/e2e-distributions' },
						{ label: 'Go: fega', slug: 'local/e2e/go-fega' },
						{ label: 'Go: egadev', slug: 'local/e2e/go-egadev' },
						{ label: 'Go: gdi', slug: 'local/e2e/go-gdi' },
						{ label: 'JUnit: FEGA', slug: 'local/e2e/junit-fega' },
						{ label: 'JUnit: EGA_DEV', slug: 'local/e2e/junit-egadev' },
						{ label: 'JUnit: GDI', slug: 'local/e2e/junit-gdi' },
					],
				},
				{
					label: 'Contributing',
					items: [
						{ label: 'Commits and pull requests', slug: 'contributing/commits' },
						{ label: 'Versioning and releases', slug: 'contributing/versioning' },
						{ label: 'CI workflows', slug: 'contributing/workflows' },
						{ label: 'The build system', slug: 'contributing/build-system' },
					],
				},
				{
					label: 'Operations',
					items: [
						{ label: 'JAR signing keys', slug: 'operations/signing-keys' },
						{ label: 'Retiring a broken release', slug: 'operations/broken-releases' },
						{ label: 'The public web pages', slug: 'operations/web-pages' },
					],
				},
				{
					label: 'Reference',
					items: [
						{ label: 'Team and links', slug: 'reference/team' },
						{ label: 'Known gaps', slug: 'reference/gaps' },
					],
				},
			],
		}),
	],
});

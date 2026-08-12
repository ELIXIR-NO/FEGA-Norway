// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import mermaid from 'astro-mermaid';

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
			customCss: ['./src/styles/theme.css'],
			components: {
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
						{ label: 'The e2e distributions', slug: 'local/e2e-distributions' },
						// One page per distribution, indented under the chooser above. Order is
						// runner first, then environment, so the retiring JUnit half stays grouped.
						{
							label: 'Distributions',
							collapsed: true,
							items: [
								{ label: 'Go: fega', slug: 'local/e2e/go-fega' },
								{ label: 'Go: egadev', slug: 'local/e2e/go-egadev' },
								{ label: 'Go: gdi', slug: 'local/e2e/go-gdi' },
								{ label: 'JUnit: FEGA', slug: 'local/e2e/junit-fega' },
								{ label: 'JUnit: EGA_DEV', slug: 'local/e2e/junit-egadev' },
								{ label: 'JUnit: GDI', slug: 'local/e2e/junit-gdi' },
							],
						},
						{ label: 'Troubleshooting', slug: 'local/troubleshooting' },
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

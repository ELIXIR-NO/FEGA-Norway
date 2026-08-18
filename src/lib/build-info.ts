import { execSync } from 'node:child_process';

// GitHub Actions exports the commit it checked out, which is authoritative in
// CI. git rev-parse covers local builds, and a tarball with no .git yields null
// so the footer simply omits the line rather than showing a wrong hash.
function resolveCommit(): string | null {
	if (process.env.GITHUB_SHA) return process.env.GITHUB_SHA;
	try {
		return execSync('git rev-parse HEAD', { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
	} catch {
		return null;
	}
}

// Module scope, so the git call happens once per build rather than once per page.
export const commit = resolveCommit();
export const shortCommit = commit?.slice(0, 7) ?? null;

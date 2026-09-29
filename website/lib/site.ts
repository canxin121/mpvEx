/**
 * @file site.ts
 * @description Central configuration file for the website's metadata, links, and constant values.
 * @module lib/site
 */

/**
 * Repository identity. This site describes a fork of mpvExtended, so every
 * repository link and API call is composed from these two values rather than
 * spelled out per call site — `lib/github.ts` reads them instead of keeping its
 * own copy.
 */
export const repo = {
  owner: "canxin121",
  name: "mpvEx",
  upstream: {
    owner: "marlboro-advance",
    name: "mpvEx",
  },
} as const;

const repoUrl = `https://github.com/${repo.owner}/${repo.name}`;

// The privacy policy is served from GitHub Pages, which the preview workflow
// (.github/workflows/preview.yml) deploys under a path named after the
// repository.
const pagesUrl = (path: string) =>
  `https://${repo.owner.toLowerCase()}.github.io/${repo.name}/${path}`;

/**
 * Global site configuration object.
 * Contains metadata, external links, and author information used throughout the application.
 */
export const siteConfig = {
  name: "mpvExtended",
  version: "v1.2.7",
  description:
    "Advanced mpv-based video player for Android with powerful features, seamless playback, and open-source freedom.",
  url: "https://mpvex.vercel.app",
  ogImage: "https://mpvex.vercel.app/og.jpg",
  icons: {
    icon: "/icon.svg",
    apple: "/apple-icon.png",
  },
  repo,
  links: {
    github: repoUrl,
    releases: `${repoUrl}/releases`,
    latestRelease: `${repoUrl}/releases/latest`,
    izzyOnAndroid: "https://apt.izzysoft.de/packages/app.marlboroadvance.mpvex",
    contributors: `${repoUrl}/graphs/contributors`,
    upstream: `https://github.com/${repo.upstream.owner}/${repo.upstream.name}`,
    privacyPolicy: pagesUrl("privacy-policy.html"),
  },
  author: {
    name: repo.owner,
    url: `https://github.com/${repo.owner}`,
  },
} as const;

export type SiteConfig = typeof siteConfig;

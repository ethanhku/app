// Builds the GitHub Pages site from web/mossling.html, the same page published as a claude.ai Artifact.
// The Artifact host wraps the page in a document skeleton; here we add that skeleton plus the
// home-screen pieces (manifest, icons, offline service worker).
import { cpSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";

const out = process.argv[2] ?? "_site";
const page = readFileSync("web/mossling.html", "utf8");
const head = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="apple-mobile-web-app-status-bar-style" content="default">
<link rel="manifest" href="manifest.webmanifest">
<link rel="apple-touch-icon" href="icon-180.png">
<link rel="icon" href="icon-192.png">
<style>:root{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}</style>
</head>
<body>
`;
const tail = `
<script>if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});</script>
</body>
</html>
`;
mkdirSync(out, { recursive: true });
writeFileSync(`${out}/index.html`, head + page + tail);
cpSync("web/static", out, { recursive: true });
console.log(`Built ${out}/index.html`);

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
const distDir = path.join(root, 'dist')
const ssrDir = path.join(root, 'dist-ssr')

const { render, routeList, SITE_URL } = await import(
  path.join(ssrDir, 'entry-server.js')
)

const template = fs.readFileSync(path.join(distDir, 'index.html'), 'utf-8')

function buildHead({ path: routePath }) {
  const url = `${SITE_URL}${routePath}`
  return [
    `<link rel="canonical" href="${url}" />`,
  ].join('\n    ')
}

for (const route of routeList) {
  const appHtml = render(route.path)
  const headHtml = buildHead(route)
  const html = template
    .replace('<!--app-head-->', headHtml)
    .replace('<!--app-html-->', appHtml)

  const outPath =
    route.path === '/'
      ? path.join(distDir, 'index.html')
      : path.join(distDir, route.path.replace(/^\//, ''), 'index.html')

  fs.mkdirSync(path.dirname(outPath), { recursive: true })
  fs.writeFileSync(outPath, html)
  console.log(`prerendered ${route.path} -> ${path.relative(root, outPath)}`)
}

const sitemap = [
  '<?xml version="1.0" encoding="UTF-8"?>',
  '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
  ...routeList.map((route) => `  <url><loc>${SITE_URL}${route.path}</loc><changefreq>${route.changeFreq}</changefreq><priority>${route.priority}</priority></url>`),
  '</urlset>',
  '',
].join('\n')
fs.writeFileSync(path.join(distDir, 'sitemap.xml'), sitemap)
console.log('wrote dist/sitemap.xml')

fs.rmSync(ssrDir, { recursive: true, force: true })

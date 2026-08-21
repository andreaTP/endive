import { readFileSync, writeFileSync, mkdirSync, readdirSync, statSync, existsSync } from 'fs';
import { join, dirname, relative, basename, extname } from 'path';

const DOCS_DIR = join(import.meta.dirname, '..', 'docs');
const BLOG_DIR = join(import.meta.dirname, '..', 'blog');
const BUILD_DIR = join(import.meta.dirname, '..', 'build');
const BASE_URL = 'https://endive.run';
const PROJECT_TITLE = 'Endive';
const PROJECT_DESC = 'Endive is a JVM native WebAssembly runtime with zero native dependencies, hosted by the Bytecode Alliance.';

function parseFrontMatter(content) {
  const match = content.match(/^---\n([\s\S]*?)\n---\n([\s\S]*)$/);
  if (!match) return { meta: {}, body: content };

  const meta = {};
  for (const line of match[1].split('\n')) {
    const kv = line.match(/^(\w[\w_-]*)\s*:\s*(.+)$/);
    if (kv) {
      let val = kv[2].trim();
      if (val === 'true') val = true;
      else if (val === 'false') val = false;
      else if (/^\d+$/.test(val)) val = parseInt(val, 10);
      else val = val.replace(/^['"]|['"]$/g, '');
      meta[kv[1]] = val;
    }
  }
  return { meta, body: match[2] };
}

function stripHtmlComments(content) {
  return content.replace(/<!--[\s\S]*?-->/g, '');
}

function cleanContent(content) {
  const { body } = parseFrontMatter(content);
  let cleaned = stripHtmlComments(body);
  cleaned = cleaned.replace(/\n{3,}/g, '\n\n');
  return cleaned.trim() + '\n';
}

function extractDescription(body) {
  const lines = stripHtmlComments(body).split('\n');
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    if (trimmed.startsWith('#')) continue;
    if (trimmed.startsWith(':::')) continue;
    if (trimmed.startsWith('![')) continue;
    if (trimmed.startsWith('```')) continue;
    let desc = trimmed.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');
    if (desc.length > 120) desc = desc.slice(0, 117) + '...';
    return desc;
  }
  return '';
}

function findMarkdownFiles(dir) {
  const results = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      results.push(...findMarkdownFiles(full));
    } else if (extname(entry) === '.md') {
      results.push(full);
    }
  }
  return results;
}

function getCategoryPosition(filePath) {
  const catFile = join(dirname(filePath), '_category_.yml');
  if (!existsSync(catFile)) return 0;
  const content = readFileSync(catFile, 'utf8');
  const match = content.match(/position:\s*(\d+)/);
  return match ? parseInt(match[1], 10) : 999;
}

function getDocEntries() {
  const files = findMarkdownFiles(DOCS_DIR);
  const entries = [];

  for (const file of files) {
    const content = readFileSync(file, 'utf8');
    const { meta, body } = parseFrontMatter(content);
    const relPath = relative(DOCS_DIR, file);

    let urlPath;
    if (relPath === 'index.md') {
      urlPath = '/docs/';
    } else if (basename(file) === 'index.md') {
      urlPath = '/docs/' + dirname(relPath) + '/';
    } else {
      urlPath = '/docs/' + relPath.replace(/\.md$/, '');
    }

    entries.push({
      file,
      relPath,
      urlPath,
      title: meta.title || meta.sidebar_label || basename(file, '.md'),
      sidebarPosition: meta.sidebar_position || 999,
      categoryPosition: relPath === 'index.md' ? -1 : getCategoryPosition(file),
      description: extractDescription(body),
      content,
    });
  }

  entries.sort((a, b) => {
    if (a.categoryPosition !== b.categoryPosition) return a.categoryPosition - b.categoryPosition;
    return a.sidebarPosition - b.sidebarPosition;
  });

  return entries;
}

function getBlogEntries() {
  if (!existsSync(BLOG_DIR)) return [];
  const files = readdirSync(BLOG_DIR)
    .filter(f => extname(f) === '.md')
    .map(f => join(BLOG_DIR, f))
    .sort()
    .reverse();

  const entries = [];
  for (const file of files) {
    const content = readFileSync(file, 'utf8');
    const { meta, body } = parseFrontMatter(content);
    if (meta.unlisted) continue;

    const slug = meta.slug || basename(file, '.md').replace(/^\d{4}-\d{2}-\d{2}-/, '');
    entries.push({
      file,
      urlPath: '/blog/' + slug,
      title: meta.title || slug,
      description: extractDescription(body),
      content,
    });
  }
  return entries;
}

function generateLlmsTxt(docEntries, blogEntries) {
  const lines = [
    `# ${PROJECT_TITLE}`,
    '',
    `> ${PROJECT_DESC}`,
    '',
    '## Docs',
    '',
  ];

  for (const entry of docEntries) {
    lines.push(`- [${entry.title}](${BASE_URL}${entry.urlPath}): ${entry.description}`);
  }

  if (blogEntries.length > 0) {
    lines.push('', '## Blog', '');
    for (const entry of blogEntries) {
      lines.push(`- [${entry.title}](${BASE_URL}${entry.urlPath}): ${entry.description}`);
    }
  }

  lines.push('', '## Optional', '');
  lines.push(`- [llms-full.txt](${BASE_URL}/llms-full.txt): Full documentation content in a single file`);
  lines.push('');

  return lines.join('\n');
}

function generateLlmsFullTxt(docEntries, blogEntries) {
  const sections = [
    `# ${PROJECT_TITLE}`,
    '',
    `> ${PROJECT_DESC}`,
    '',
  ];

  for (const entry of docEntries) {
    sections.push('---', '');
    sections.push(`## ${entry.title}`);
    sections.push('');
    sections.push(cleanContent(entry.content));
  }

  if (blogEntries.length > 0) {
    sections.push('---', '', '# Blog Posts', '',
      '> The following are blog posts, not reference documentation.', '');
  }

  for (const entry of blogEntries) {
    sections.push('---', '');
    sections.push(`## ${entry.title}`);
    sections.push('');
    sections.push(cleanContent(entry.content));
  }

  return sections.join('\n');
}

function copyCleanedMarkdown(docEntries) {
  for (const entry of docEntries) {
    const outPath = join(BUILD_DIR, 'docs', entry.relPath);
    mkdirSync(dirname(outPath), { recursive: true });
    writeFileSync(outPath, cleanContent(entry.content), 'utf8');
  }
}

function docUrlToHtmlPath(entry) {
  const rel = entry.relPath;
  if (rel === 'index.md') {
    return join(BUILD_DIR, 'docs', 'index.html');
  }
  if (basename(rel) === 'index.md') {
    return join(BUILD_DIR, 'docs', dirname(rel), 'index.html');
  }
  return join(BUILD_DIR, 'docs', rel.replace(/\.md$/, ''), 'index.html');
}

function injectAlternateLinks(docEntries) {
  let injected = 0;
  for (const entry of docEntries) {
    const htmlPath = docUrlToHtmlPath(entry);
    if (!existsSync(htmlPath)) {
      console.warn(`  warn: HTML not found: ${htmlPath}`);
      continue;
    }

    const mdUrl = '/docs/' + entry.relPath;
    const linkTag = `<link rel="alternate" type="text/markdown" href="${mdUrl}">`;
    let html = readFileSync(htmlPath, 'utf8');

    if (html.includes('rel="alternate" type="text/markdown"')) continue;

    html = html.replace('</head>', `${linkTag}</head>`);
    writeFileSync(htmlPath, html, 'utf8');
    injected++;
  }
  return injected;
}

console.log('Generating LLM-friendly files...');

const docEntries = getDocEntries();
const blogEntries = getBlogEntries();

console.log(`  Found ${docEntries.length} doc pages, ${blogEntries.length} published blog posts`);

const llmsTxt = generateLlmsTxt(docEntries, blogEntries);
writeFileSync(join(BUILD_DIR, 'llms.txt'), llmsTxt, 'utf8');
console.log(`  Generated llms.txt (${llmsTxt.length} bytes)`);

const llmsFullTxt = generateLlmsFullTxt(docEntries, blogEntries);
writeFileSync(join(BUILD_DIR, 'llms-full.txt'), llmsFullTxt, 'utf8');
console.log(`  Generated llms-full.txt (${llmsFullTxt.length} bytes)`);

copyCleanedMarkdown(docEntries);
console.log(`  Copied ${docEntries.length} cleaned .md files`);

const injected = injectAlternateLinks(docEntries);
console.log(`  Injected ${injected} <link rel="alternate"> tags`);

console.log('Done.');

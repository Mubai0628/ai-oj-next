function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function renderInline(value: string) {
  const codePlaceholders: string[] = [];
  const escaped = escapeHtml(value).replace(/`([^`]+?)`/g, (_, code: string) => {
    codePlaceholders.push(`<code>${code}</code>`);
    return `\u0000CODE${codePlaceholders.length - 1}\u0000`;
  });

  return escaped
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\u0000CODE(\d+)\u0000/g, (_, index: string) => codePlaceholders[Number(index)] || '');
}

export function renderMarkdownLite(markdown: string) {
  const lines = markdown.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n');
  const html: string[] = [];
  const paragraphLines: string[] = [];
  const codeLines: string[] = [];
  let inCodeBlock = false;
  let listType: 'ul' | 'ol' | null = null;

  const closeParagraph = () => {
    if (!paragraphLines.length) return;
    html.push(`<p>${paragraphLines.map((line) => renderInline(line)).join('<br>')}</p>`);
    paragraphLines.length = 0;
  };

  const closeList = () => {
    if (!listType) return;
    html.push(`</${listType}>`);
    listType = null;
  };

  const ensureList = (type: 'ul' | 'ol') => {
    closeParagraph();
    if (listType === type) return;
    closeList();
    html.push(`<${type}>`);
    listType = type;
  };

  const closeCodeBlock = () => {
    html.push(`<pre><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`);
    codeLines.length = 0;
    inCodeBlock = false;
  };

  for (const line of lines) {
    if (line.trim().startsWith('```')) {
      if (inCodeBlock) {
        closeCodeBlock();
      } else {
        closeParagraph();
        closeList();
        inCodeBlock = true;
      }
      continue;
    }

    if (inCodeBlock) {
      codeLines.push(line);
      continue;
    }

    if (!line.trim()) {
      closeParagraph();
      closeList();
      continue;
    }

    const heading = line.match(/^(#{1,6})\s+(.+)$/);
    if (heading) {
      closeParagraph();
      closeList();
      const level = heading[1].length;
      html.push(`<h${level}>${renderInline(heading[2].trim())}</h${level}>`);
      continue;
    }

    const unorderedItem = line.match(/^\s*[-*]\s+(.+)$/);
    if (unorderedItem) {
      ensureList('ul');
      html.push(`<li>${renderInline(unorderedItem[1].trim())}</li>`);
      continue;
    }

    const orderedItem = line.match(/^\s*\d+[.)]\s+(.+)$/);
    if (orderedItem) {
      ensureList('ol');
      html.push(`<li>${renderInline(orderedItem[1].trim())}</li>`);
      continue;
    }

    closeList();
    paragraphLines.push(line.trim());
  }

  if (inCodeBlock) closeCodeBlock();
  closeParagraph();
  closeList();
  return html.join('');
}

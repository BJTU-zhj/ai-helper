import MarkdownIt from "markdown-it";
import hljs from "highlight.js";

function escapeHtml(input: string): string {
  return input
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

const markdown: MarkdownIt = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  highlight(code: string, language: string): string {
    const hasLanguage = !!language && hljs.getLanguage(language);
    if (hasLanguage) {
      try {
        const highlighted = hljs.highlight(code, { language, ignoreIllegals: true }).value;
        return `<pre class="hljs"><code>${highlighted}</code></pre>`;
      } catch (error) {
        console.warn("highlight failed", error);
      }
    }
    const escaped = escapeHtml(code);
    return `<pre class="hljs"><code>${escaped}</code></pre>`;
  }
});

export function renderMarkdown(content: string): string {
  return markdown.render(content || "");
}

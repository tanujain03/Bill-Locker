/**
 * A QR code can hold any text. Only real web addresses (http/https) are turned into
 * links; anything else (e.g. "javascript:…") is shown as plain text.
 */
export function safeWebLink(value: string): { href: string; host: string } | null {
  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' && url.protocol !== 'http:') return null;
    return { href: url.href, host: url.host };
  } catch {
    return null;
  }
}

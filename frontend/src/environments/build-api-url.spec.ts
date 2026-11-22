import { describe, expect, it } from 'vitest';
import { resolveBuildApiUrl } from './build-api-url';

const FALLBACK = 'https://esibuy-2627.onrender.com';

describe('resolveBuildApiUrl', () => {
  it('uses the URL given at build time', () => {
    expect(resolveBuildApiUrl('https://abc.trycloudflare.com', FALLBACK)).toBe('https://abc.trycloudflare.com');
  });

  it('trims spaces and the trailing slash', () => {
    expect(resolveBuildApiUrl('  https://abc.trycloudflare.com/ ', FALLBACK)).toBe('https://abc.trycloudflare.com');
  });

  it('falls back to the default server when no URL was given', () => {
    expect(resolveBuildApiUrl(undefined, FALLBACK)).toBe(FALLBACK);
    expect(resolveBuildApiUrl('', FALLBACK)).toBe(FALLBACK);
    expect(resolveBuildApiUrl('   ', FALLBACK)).toBe(FALLBACK);
  });

  it('rejects values that are not http or https URLs', () => {
    expect(() => resolveBuildApiUrl('javascript:alert(1)', FALLBACK)).toThrow(/ESIBUY_API_URL/);
    expect(() => resolveBuildApiUrl('esibuy.local:8080', FALLBACK)).toThrow(/ESIBUY_API_URL/);
    expect(() => resolveBuildApiUrl(42, FALLBACK)).toThrow(/ESIBUY_API_URL/);
  });
});

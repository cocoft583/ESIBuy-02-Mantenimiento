import { afterEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { apiBaseUrl } from './api-url';

describe('apiBaseUrl', () => {
  const originalApiUrl = environment.apiUrl;

  afterEach(() => {
    environment.apiUrl = originalApiUrl;
    localStorage.removeItem('esibuy_api_url');
  });

  it('returns the API URL fixed at build time', () => {
    environment.apiUrl = 'https://api.esibuy.test';

    expect(apiBaseUrl()).toBe('https://api.esibuy.test');
  });

  it('ignores an API URL written in localStorage', () => {
    environment.apiUrl = 'https://api.esibuy.test';
    localStorage.setItem('esibuy_api_url', 'https://attacker.example');

    expect(apiBaseUrl()).toBe('https://api.esibuy.test');
  });

  it('keeps requests relative when the build has no API URL, even with one in localStorage', () => {
    environment.apiUrl = '';
    localStorage.setItem('esibuy_api_url', 'https://attacker.example');

    expect(apiBaseUrl()).toBe('');
  });

  it('removes the trailing slash so paths can be appended', () => {
    environment.apiUrl = 'https://api.esibuy.test/';

    expect(apiBaseUrl()).toBe('https://api.esibuy.test');
  });
});

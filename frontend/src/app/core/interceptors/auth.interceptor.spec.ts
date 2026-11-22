import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let clearCurrentUser: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    clearCurrentUser = vi.fn();
    navigate = vi.fn().mockResolvedValue(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { clearCurrentUser } },
        { provide: Router, useValue: { navigate } },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('clears local authentication and redirects after a protected request returns 401', () => {
    http.get('/api/products/summary').subscribe({ error: () => undefined });

    httpTesting.expectOne('/api/products/summary').flush(
      { message: 'Autenticación requerida' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(clearCurrentUser).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/'], {
      queryParams: { expired: 'true' },
    });
  });

  it('does not redirect for login, session lookup, or logout responses', () => {
    http.post('/api/users/login', {}).subscribe({ error: () => undefined });
    httpTesting.expectOne('/api/users/login').flush({}, {
      status: 401,
      statusText: 'Unauthorized',
    });

    http.get('/api/users/me').subscribe({ error: () => undefined });
    httpTesting.expectOne('/api/users/me').flush({}, {
      status: 401,
      statusText: 'Unauthorized',
    });

    http.post('/api/users/logout', {}).subscribe({ error: () => undefined });
    httpTesting.expectOne('/api/users/logout').flush({}, {
      status: 401,
      statusText: 'Unauthorized',
    });

    expect(clearCurrentUser).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('redirects with the revoked notice when the server closes the session', () => {
    http.get('/api/cart').subscribe({ error: () => undefined });

    httpTesting.expectOne('/api/cart').flush(
      { success: false, code: 'SESSION_REVOKED' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(clearCurrentUser).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/'], {
      queryParams: { revoked: 'true' },
    });
  });

  describe('URL de la API', () => {
    const originalApiUrl = environment.apiUrl;

    afterEach(() => {
      environment.apiUrl = originalApiUrl;
      localStorage.removeItem('esibuy_api_url');
    });

    it('sends API requests to the server fixed at build time even if localStorage has another one', () => {
      environment.apiUrl = 'https://api.esibuy.test';
      localStorage.setItem('esibuy_api_url', 'https://attacker.example');

      http.post('/api/users/login', { email: 'ana@example.com' }).subscribe();

      const request = httpTesting.expectOne('https://api.esibuy.test/api/users/login');
      expect(request.request.withCredentials).toBe(true);
      request.flush({});
      httpTesting.expectNone((req) => req.url.startsWith('https://attacker.example'));
    });

    it('keeps API requests relative when the build has no API URL, even if localStorage has one', () => {
      environment.apiUrl = '';
      localStorage.setItem('esibuy_api_url', 'https://attacker.example');

      http.get('/api/products').subscribe();

      httpTesting.expectOne('/api/products').flush([]);
      httpTesting.expectNone((req) => req.url.startsWith('https://attacker.example'));
    });
  });
});

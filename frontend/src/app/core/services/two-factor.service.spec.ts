import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TwoFactorService } from './two-factor.service';

describe('TwoFactorService', () => {
  let service: TwoFactorService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TwoFactorService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    TestBed.resetTestingModule();
  });

  it('should send an empty body on the first setup', () => {
    service.setup().subscribe();

    const request = httpTesting.expectOne('/api/users/2fa/setup');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({ secret: 'SECRET', qrUri: 'otpauth://totp/ESIBuy:a?secret=SECRET' });
  });

  it('should send the current code when reconfiguring', () => {
    service.setup('123456').subscribe();

    const request = httpTesting.expectOne('/api/users/2fa/setup');
    expect(request.request.body).toEqual({ code: '123456' });
    request.flush({ secret: 'NEW', qrUri: 'otpauth://totp/ESIBuy:a?secret=NEW' });
  });
});

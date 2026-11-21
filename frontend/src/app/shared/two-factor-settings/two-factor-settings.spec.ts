import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { EmailOtpSettingsService } from '../../core/services/email-otp-settings.service';
import { TwoFactorService } from '../../core/services/two-factor.service';
import { TwoFactorSettingsComponent } from './two-factor-settings';

describe('TwoFactorSettingsComponent', () => {
  let fixture: ComponentFixture<TwoFactorSettingsComponent>;
  let component: TwoFactorSettingsComponent;
  let twoFactorService: {
    setup: ReturnType<typeof vi.fn>;
    enable: ReturnType<typeof vi.fn>;
    regenerateBackupCodes: ReturnType<typeof vi.fn>;
    disable: ReturnType<typeof vi.fn>;
    getErrorMessage: ReturnType<typeof vi.fn>;
  };

  const newSetup = {
    secret: 'NEWSECRET',
    qrUri: 'otpauth://totp/ESIBuy:ana@example.com?secret=NEWSECRET&issuer=ESIBuy',
  };

  beforeEach(async () => {
    twoFactorService = {
      setup: vi.fn().mockReturnValue(of(newSetup)),
      enable: vi.fn(),
      regenerateBackupCodes: vi.fn(),
      disable: vi.fn(),
      getErrorMessage: vi.fn((error: { error?: { message?: string } }, fallback: string) =>
        error.error?.message ?? fallback),
    };

    await TestBed.configureTestingModule({
      imports: [TwoFactorSettingsComponent],
      providers: [
        { provide: TwoFactorService, useValue: twoFactorService },
        {
          provide: EmailOtpSettingsService,
          useValue: {
            getSettings: vi.fn().mockReturnValue(of({ enabled: false, email: 'ana@example.com', totpEnabled: true })),
            updateEnabled: vi.fn(),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TwoFactorSettingsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    fixture.destroy();
  });

  it('should start the first setup without asking for a code', () => {
    component.enabled = false;
    fixture.detectChanges();

    component.setupTwoFactor();

    expect(twoFactorService.setup).toHaveBeenCalledWith();
  });

  it('should show the reconfiguration field and its warning when two-factor is enabled', () => {
    component.enabled = true;
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('#reconfigure-two-factor-code')).not.toBeNull();
    expect(element.textContent).toContain('tu app actual dejará de funcionar');
  });

  it('should not reconfigure without the six digits of the current code', () => {
    component.enabled = true;
    fixture.detectChanges();
    component.reconfigureCode = '12';

    component.reconfigureTwoFactor();

    expect(component.twoFactorError).toBe('Introduce los seis dígitos de tu aplicación de autenticación.');
    expect(twoFactorService.setup).not.toHaveBeenCalled();
  });

  it('should send the current code without spaces when reconfiguring', () => {
    component.enabled = true;
    fixture.detectChanges();
    component.reconfigureCode = '123 456';

    component.reconfigureTwoFactor();

    expect(twoFactorService.setup).toHaveBeenCalledWith('123456');
  });

  it('should show the backend error and keep the current setup when the code is wrong', () => {
    twoFactorService.setup.mockReturnValue(throwError(() => ({
      status: 400,
      error: { message: 'El código introducido es incorrecto' },
    })));
    component.enabled = true;
    fixture.detectChanges();
    component.reconfigureCode = '000000';

    component.reconfigureTwoFactor();
    fixture.detectChanges();

    expect(component.twoFactorError).toBe('El código introducido es incorrecto');
    expect(component.twoFactorSetup).toBeNull();
    expect(component.twoFactorEnabled).toBe(true);
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent)
      .toContain('El código introducido es incorrecto');
  });

  it('should show the new key and the confirmation step after a valid code', async () => {
    component.enabled = true;
    fixture.detectChanges();
    component.reconfigureCode = '123456';

    component.reconfigureTwoFactor();
    await fixture.whenStable();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(component.twoFactorSetup).toEqual(newSetup);
    expect(element.querySelector('#two-factor-secret')?.textContent).toContain('NEWSECRET');
    expect(element.querySelector('#enable-two-factor-code')).not.toBeNull();
    expect(element.querySelector('#reconfigure-two-factor-code')).toBeNull();
    expect(component.reconfigureCode).toBe('');
  });
});

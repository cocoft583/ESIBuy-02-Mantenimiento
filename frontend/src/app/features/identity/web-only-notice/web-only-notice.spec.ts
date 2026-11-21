import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AuthenticatedUser, AuthService, UserRole } from '../../../core/services/auth.service';
import { WebOnlyNotice } from './web-only-notice';

describe('WebOnlyNotice', () => {
  let fixture: ComponentFixture<WebOnlyNotice>;
  let logout: ReturnType<typeof vi.fn>;
  const currentUser = signal<AuthenticatedUser | null>(null);

  function render(role: UserRole): HTMLElement {
    currentUser.set({ id: 'user-1', email: 'user@example.com', role, active: true });
    fixture = TestBed.createComponent(WebOnlyNotice);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    logout = vi.fn().mockReturnValue(of(undefined));
    TestBed.configureTestingModule({
      imports: [WebOnlyNotice],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { currentUser: currentUser.asReadonly(), logout } },
      ],
    });
  });

  it('explica al vendedor que su tienda se gestiona desde la web', () => {
    const el = render('SELLER');

    expect(el.querySelector('h1')?.textContent).toContain('tienda');
    expect(el.textContent).toContain('La app de ESIBuy es para clientes');
  });

  it('explica al administrador que la administración se hace desde la web', () => {
    const el = render('ADMIN');

    expect(el.querySelector('h1')?.textContent).toContain('administración');
  });

  it('ofrece abrir ESIBuy en la web, fuera de la app', () => {
    const link = render('SELLER').querySelector('a.open-web') as HTMLAnchorElement;

    expect(link.getAttribute('href')).toBe('https://esibuy-2627.web.app');
    expect(link.getAttribute('target')).toBe('_blank');
  });

  it('al cerrar sesión vuelve al inicio de sesión para entrar como cliente', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const el = render('SELLER');

    (el.querySelector('button.logout') as HTMLButtonElement).click();

    expect(logout).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});

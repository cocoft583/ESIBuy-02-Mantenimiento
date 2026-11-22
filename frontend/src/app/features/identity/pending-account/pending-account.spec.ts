import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PendingAccount } from './pending-account';

describe('PendingAccount', () => {
  let fixture: ComponentFixture<PendingAccount>;
  let element: HTMLElement;

  function render(state: Record<string, unknown> | null = null): void {
    history.replaceState(state, '');
    fixture = TestBed.createComponent(PendingAccount);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  function tipText(): string {
    return element.querySelector('.tip-text')?.textContent?.trim() ?? '';
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PendingAccount],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => {
    vi.useRealTimers();
    history.replaceState(null, '');
  });

  it('tells the user that an administrator has to approve the account (HU17)', () => {
    render();

    expect(element.querySelector('h1')?.textContent).toContain('Tu cuenta está pendiente de aprobación');
    expect(element.querySelector('.pending-lead')?.textContent)
      .toContain('Tu cuenta está pendiente de aprobación por un administrador');
    expect(element.textContent).toContain('Tu contraseña es correcta');
  });

  it('shows the account and the message that the login sent', () => {
    render({ email: 'ana@example.com', message: 'Mensaje del servidor' });

    expect(element.querySelector('.pending-account-email')?.textContent).toContain('ana@example.com');
    expect(element.querySelector('.pending-lead')?.textContent).toContain('Mensaje del servidor');
  });

  it('does not show an account when the page is opened directly', () => {
    render();

    expect(element.querySelector('.pending-account-email')).toBeNull();
  });

  it('shows where the user is: registered, waiting for review and access still to come', () => {
    render();

    const steps = Array.from(element.querySelectorAll('.pending-steps li'));
    expect(steps).toHaveLength(3);
    expect(steps[0].classList).toContain('is-done');
    expect(steps[0].textContent).toContain('Registro completado');
    expect(steps[1].classList).toContain('is-current');
    expect(steps[1].getAttribute('aria-current')).toBe('step');
    expect(steps[1].textContent).toContain('Revisión de un administrador');
    expect(steps[2].classList).toContain('is-upcoming');
    expect(steps[2].textContent).toContain('Acceso a ESIBuy');
  });

  it('rotates the tips every few seconds', () => {
    vi.useFakeTimers();
    render();
    const first = tipText();

    vi.advanceTimersByTime(6000);
    fixture.detectChanges();

    expect(tipText()).not.toBe(first);
    expect(element.querySelector('.tip-dots [aria-current="true"]')).not.toBeNull();
  });

  it('lets the user pause the tips and pick one', () => {
    vi.useFakeTimers();
    render();
    const pause = element.querySelector('.tip-pause') as HTMLButtonElement;
    expect(pause.getAttribute('aria-label')).toBe('Pausar los consejos');

    pause.click();
    fixture.detectChanges();
    const paused = tipText();
    vi.advanceTimersByTime(20000);
    fixture.detectChanges();
    expect(tipText()).toBe(paused);
    expect(pause.getAttribute('aria-label')).toBe('Reanudar los consejos');

    const dots = element.querySelectorAll<HTMLButtonElement>('.tip-dots button');
    dots[2].click();
    fixture.detectChanges();
    expect(dots[2].getAttribute('aria-current')).toBe('true');
    expect(tipText()).not.toBe('');
  });

  it('does not rotate the tips when the user prefers reduced motion', () => {
    vi.useFakeTimers();
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: vi.fn((query: string) => ({ matches: query.includes('prefers-reduced-motion: reduce'), media: query })),
    });
    try {
      render();
      const first = tipText();

      vi.advanceTimersByTime(20000);
      fixture.detectChanges();

      expect(tipText()).toBe(first);
    } finally {
      Reflect.deleteProperty(window, 'matchMedia');
    }
  });

  it('goes back to the login keeping the email so the user does not type it again', () => {
    render({ email: 'ana@example.com' });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (element.querySelector('.pending-retry') as HTMLButtonElement).click();

    expect(navigate).toHaveBeenCalledWith(['/login'], { state: { loginEmail: 'ana@example.com' } });
  });

  it('offers the public catalog while waiting', () => {
    render();

    expect(element.querySelector('a.pending-catalog')?.getAttribute('href')).toBe('/');
  });

  it('stops the tip timer when the page goes away', () => {
    vi.useFakeTimers();
    render();

    fixture.destroy();

    expect(vi.getTimerCount()).toBe(0);
  });
});

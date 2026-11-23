import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AlertsService } from '../../core/services/alerts.service';
import { AlertsBell } from './alerts-bell';

describe('AlertsBell', () => {
  let fixture: ComponentFixture<AlertsBell>;
  let unreadCount: ReturnType<typeof signal<number>>;
  let startWatching: ReturnType<typeof vi.fn>;

  function badge(): string | null {
    return fixture.nativeElement.querySelector('.alerts-badge')?.textContent?.trim() ?? null;
  }

  function hiddenText(): string | null {
    return fixture.nativeElement.querySelector('.visually-hidden')?.textContent?.trim() ?? null;
  }

  beforeEach(() => {
    unreadCount = signal(0);
    startWatching = vi.fn();
    TestBed.configureTestingModule({
      imports: [AlertsBell],
      providers: [{ provide: AlertsService, useValue: { unreadCount: unreadCount.asReadonly(), startWatching } }],
    });
    fixture = TestBed.createComponent(AlertsBell);
    fixture.detectChanges();
  });

  it('empieza a seguir el contador al aparecer', () => {
    expect(startWatching).toHaveBeenCalledOnce();
  });

  it('sin alertas sin leer no enseña contador', () => {
    expect(badge()).toBeNull();
    expect(hiddenText()).toBeNull();
  });

  it('enseña el número de alertas sin leer y lo anuncia a los lectores de pantalla', () => {
    unreadCount.set(3);
    fixture.detectChanges();

    expect(badge()).toBe('3');
    expect(fixture.nativeElement.querySelector('.alerts-badge').getAttribute('aria-hidden')).toBe('true');
    expect(hiddenText()).toBe('3 alertas sin leer');
  });

  it('habla en singular con una sola alerta', () => {
    unreadCount.set(1);
    fixture.detectChanges();

    expect(hiddenText()).toBe('1 alerta sin leer');
  });

  it('se actualiza sola cuando cambia el contador', () => {
    unreadCount.set(2);
    fixture.detectChanges();
    unreadCount.set(0);
    fixture.detectChanges();

    expect(badge()).toBeNull();
  });

  it('a partir de 100 enseña 99+', () => {
    unreadCount.set(120);
    fixture.detectChanges();

    expect(badge()).toBe('99+');
    expect(hiddenText()).toBe('120 alertas sin leer');
  });
});

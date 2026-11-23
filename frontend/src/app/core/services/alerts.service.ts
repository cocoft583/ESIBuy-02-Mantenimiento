import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { Observable, catchError, filter, fromEvent, map, merge, of, switchMap, tap, timer } from 'rxjs';
import { AuthService } from './auth.service';

/** Motivo de la alerta: bajada de precio o vuelta a estar disponible (HU27) y producto nuevo (HU28). */
export type AlertType = 'PRICE_DROP' | 'AVAILABILITY' | 'NEW_PRODUCT';

export interface CustomerAlert {
  id: string;
  type: AlertType;
  productId: string;
  productName: string;
  message: string;
  createdAt: string;
  read: boolean;
}

/** Cada cuánto se vuelve a pedir el número de alertas sin leer mientras la página está a la vista. */
export const UNREAD_POLL_INTERVAL_MS = 60_000;

const API = '/api/customer/alerts';

/**
 * Bandeja de alertas del cliente (HU29). Además de las operaciones de la bandeja, mantiene
 * el número de alertas sin leer que enseña la campana: se pide al empezar, al cambiar de
 * página y cada minuto, solo si hay un cliente con sesión y la pestaña está a la vista.
 */
@Injectable({ providedIn: 'root' })
export class AlertsService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private readonly unreadCountSignal = signal(0);
  private watching = false;

  readonly unreadCount = this.unreadCountSignal.asReadonly();

  getAlerts(): Observable<CustomerAlert[]> {
    return this.http.get<CustomerAlert[] | null>(API, { withCredentials: true })
      .pipe(map((alerts) => alerts ?? []));
  }

  markAsRead(alertId: string): Observable<void> {
    return this.http.patch<void>(`${API}/${encodeURIComponent(alertId)}/read`, null, { withCredentials: true })
      .pipe(tap(() => this.refreshUnreadCount()));
  }

  markAllAsRead(): Observable<void> {
    return this.http.patch<void>(`${API}/read-all`, null, { withCredentials: true })
      .pipe(tap(() => this.unreadCountSignal.set(0)));
  }

  delete(alertId: string): Observable<void> {
    return this.http.delete<void>(`${API}/${encodeURIComponent(alertId)}`, { withCredentials: true })
      .pipe(tap(() => this.refreshUnreadCount()));
  }

  refreshUnreadCount(): void {
    this.fetchUnreadCount().subscribe((count) => this.unreadCountSignal.set(count));
  }

  /** Empieza a seguir el contador. Llamarlo más de una vez no crea más peticiones. */
  startWatching(): void {
    if (this.watching || !this.isBrowser) {
      return;
    }
    this.watching = true;

    const navigations = this.router.events.pipe(filter((event) => event instanceof NavigationEnd));
    const backToTab = fromEvent(this.document, 'visibilitychange').pipe(filter(() => this.isVisible()));

    merge(timer(0, UNREAD_POLL_INTERVAL_MS).pipe(filter(() => this.isVisible())), navigations, backToTab)
      .pipe(
        switchMap(() => this.fetchUnreadCount()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((count) => this.unreadCountSignal.set(count));
  }

  private fetchUnreadCount(): Observable<number> {
    if (this.authService.currentUser()?.role !== 'CLIENT') {
      return of(0);
    }

    return this.http.get<{ count?: number | null }>(`${API}/unread-count`, { withCredentials: true }).pipe(
      map((response) => Math.max(0, Number(response?.count ?? 0))),
      // Si falla (sin red, sesión caducada) se queda la última cifra en lugar de borrar el aviso
      catchError(() => of(this.unreadCountSignal())),
    );
  }

  private isVisible(): boolean {
    return this.document.visibilityState !== 'hidden';
  }
}

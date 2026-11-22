import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

const DEFAULT_MESSAGE = 'Tu cuenta está pendiente de aprobación por un administrador.';
const TIP_INTERVAL_MS = 5000;

export const PENDING_TIPS: readonly string[] = [
  'Mientras tanto puedes ver el catálogo y los productos sin iniciar sesión.',
  'Cuando te aprueben, entra con el mismo correo y la misma contraseña.',
  'No hace falta que te registres otra vez: tu alta ya está guardada.',
  'Si intentas entrar antes de tiempo no pasa nada: estos intentos no bloquean tu cuenta.',
];

interface PendingState {
  email?: unknown;
  message?: unknown;
}

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined'
    && window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches === true;
}

/**
 * Página a la que llega quien inicia sesión con la contraseña correcta pero con la cuenta
 * todavía sin aprobar (HU17). El correo y el mensaje llegan en el estado de la navegación,
 * no en la URL, para no dejar datos personales en el historial.
 */
@Component({
  selector: 'app-pending-account',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './pending-account.html',
  styleUrl: './pending-account.css',
})
export class PendingAccount implements OnInit {
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly email: string | null;
  readonly message: string;
  readonly tips = PENDING_TIPS;
  readonly tipIndex = signal(0);
  readonly tipsPaused = signal(false);

  constructor() {
    const state = (typeof history !== 'undefined' ? history.state : null) as PendingState | null;
    this.email = typeof state?.email === 'string' && state.email.trim() ? state.email.trim() : null;
    this.message = typeof state?.message === 'string' && state.message.trim() ? state.message : DEFAULT_MESSAGE;
    this.destroyRef.onDestroy(() => this.stopTips());
  }

  ngOnInit(): void {
    // Con «reducir movimiento» los consejos no cambian solos; se pueden elegir con los puntos
    if (prefersReducedMotion()) {
      this.tipsPaused.set(true);
      return;
    }
    this.startTips();
  }

  toggleTips(): void {
    if (this.tipsPaused()) {
      this.tipsPaused.set(false);
      this.startTips();
    } else {
      this.tipsPaused.set(true);
      this.stopTips();
    }
  }

  showTip(index: number): void {
    this.tipIndex.set(index);
    if (!this.tipsPaused()) {
      // Se reinicia el contador para que el consejo elegido se vea entero
      this.startTips();
    }
  }

  backToLogin(): void {
    void this.router.navigate(['/login'], { state: { loginEmail: this.email ?? undefined } });
  }

  private startTips(): void {
    this.stopTips();
    this.timer = setInterval(() => {
      this.tipIndex.update((index) => (index + 1) % this.tips.length);
    }, TIP_INTERVAL_MS);
  }

  private stopTips(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}

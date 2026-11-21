import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

const WEB_URL = 'https://esibuy-2627.web.app';

/** Pantalla de la app móvil para vendedores y administradores: su trabajo se hace desde la web. */
@Component({
  selector: 'app-web-only-notice',
  standalone: true,
  templateUrl: './web-only-notice.html',
  styleUrl: './web-only-notice.css',
})
export class WebOnlyNotice {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly webUrl = WEB_URL;
  readonly isAdmin = computed(() => this.authService.currentUser()?.role === 'ADMIN');

  logout(): void {
    this.authService.logout().subscribe({
      next: () => void this.router.navigate(['/login']),
      error: () => {
        this.authService.clearCurrentUser();
        void this.router.navigate(['/login']);
      },
    });
  }
}

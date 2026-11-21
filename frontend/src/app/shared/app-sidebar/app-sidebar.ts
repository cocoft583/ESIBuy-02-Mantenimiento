import { isPlatformBrowser } from '@angular/common';
import { Component, EventEmitter, Inject, Input, OnInit, Output, PLATFORM_ID } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

export type SidebarVariant = 'admin' | 'seller' | 'seller-compact' | 'client';

// En móvil la barra va encima del contenido, así que plegarla no tiene sentido.
const MOBILE_QUERY = '(max-width: 760px)';

@Component({
  selector: 'app-shared-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './app-sidebar.html',
  styleUrl: './app-sidebar.css',
})
export class AppSidebarComponent implements OnInit {
  @Input({ required: true }) variant: SidebarVariant = 'seller';
  @Input() collapsed = false;
  @Output() readonly collapsedChange = new EventEmitter<boolean>();

  animateTransition = false;
  isMobile = false;

  constructor(@Inject(PLATFORM_ID) private readonly platformId: object) { }

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    this.isMobile = window.matchMedia?.(MOBILE_QUERY).matches ?? false;
    if (this.isMobile) {
      this.collapsedChange.emit(false);
      return;
    }

    const storedState = localStorage.getItem(this.storageKey());
    if (storedState === 'true' || storedState === 'false') {
      this.collapsedChange.emit(storedState === 'true');
    }
  }

  toggle(): void {
    this.animateTransition = true;
    const collapsed = !this.collapsed;

    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(this.storageKey(), String(collapsed));
    }

    this.collapsedChange.emit(collapsed);
  }

  private storageKey(): string {
    switch (this.variant) {
      case 'admin':
        return 'admin-sidebar-collapsed';
      case 'seller':
      case 'seller-compact':
        return 'seller-sidebar-collapsed';
      case 'client':
        return 'client-sidebar-collapsed';
      default:
        return 'seller-sidebar-collapsed';
    }
  }
}

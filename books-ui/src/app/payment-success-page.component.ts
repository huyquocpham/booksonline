import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { PaymentApi } from './payment-api';

@Component({
  selector: 'app-payment-success-page',
  imports: [CommonModule, RouterLink],
  templateUrl: './payment-success-page.component.html',
  styleUrl: './payment-status-page.css'
})
export class PaymentSuccessPageComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly paymentApi = inject(PaymentApi);
  private readonly router = inject(Router);

  protected readonly isLoading = signal(true);
  protected readonly message = signal('Confirming payment with Stripe...');
  protected readonly hasError = signal(false);

  constructor() {
    const sessionId = this.route.snapshot.queryParamMap.get('session_id');
    if (!sessionId) {
      this.isLoading.set(false);
      this.hasError.set(true);
      this.message.set('Missing Stripe session id.');
      return;
    }

    this.paymentApi.confirmPayment(sessionId).subscribe({
      next: (response) => {
        this.isLoading.set(false);
        this.hasError.set(!response.paid || !response.checkoutCompleted);
        this.message.set(response.message);
      },
      error: (error) => {
        this.isLoading.set(false);
        this.hasError.set(true);
        this.message.set(error?.error?.message ?? 'Payment confirmation failed.');
      }
    });
  }

  protected backToApp(): void {
    void this.router.navigateByUrl('/app');
  }
}

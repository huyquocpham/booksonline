import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { DEMO_CUSTOMER_ID } from './cart-api';

export interface CheckoutSessionResponse {
  id: string;
  url: string | null;
  paymentStatus: string;
  status: string;
}

export interface PaymentConfirmationResponse {
  sessionId: string;
  paid: boolean;
  checkoutCompleted: boolean;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class PaymentApi {
  private readonly http = inject(HttpClient);
  private readonly customerId = DEMO_CUSTOMER_ID;
  private readonly baseUrl = `http://localhost:8085/api/payments`;

  createCheckoutSession(): Observable<CheckoutSessionResponse> {
    return this.http.post<CheckoutSessionResponse>(`${this.baseUrl}/checkout/${this.customerId}`, {});
  }

  confirmPayment(sessionId: string): Observable<PaymentConfirmationResponse> {
    return this.http.post<PaymentConfirmationResponse>(
      `${this.baseUrl}/confirm/${this.customerId}`,
      {},
      { params: new HttpParams().set('sessionId', sessionId) }
    );
  }
}

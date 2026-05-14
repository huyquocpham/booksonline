import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CartItemRequest {
  bookId: number;
  quantity: number;
}

export interface CartItem {
  bookId: number;
  title: string;
  isbn: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface Cart {
  cartId: string;
  customerId: string;
  items: CartItem[];
  totalAmount: number;
}

@Injectable({ providedIn: 'root' })
export class CartApi {
  private readonly http = inject(HttpClient);
  private readonly customerId = 'customer-100';
  private readonly baseUrl = `http://localhost:8082/api/carts/${this.customerId}`;

  getCart(): Observable<Cart> {
    return this.http.get<Cart>(this.baseUrl);
  }

  addItem(request: CartItemRequest): Observable<Cart> {
    return this.http.post<Cart>(`${this.baseUrl}/items`, request);
  }

  removeItem(bookId: number): Observable<Cart> {
    return this.http.delete<Cart>(`${this.baseUrl}/items/${bookId}`);
  }

  checkout(): Observable<Cart> {
    return this.http.post<Cart>(`${this.baseUrl}/checkout`, {});
  }
}

import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

import { Cart } from './cart-api';

@Component({
  selector: 'app-shopping-cart',
  imports: [CommonModule],
  templateUrl: './shopping-cart.component.html',
  styleUrl: './shopping-cart.component.css'
})
export class ShoppingCartComponent {
  @Input({ required: true }) cart: Cart | null = null;
  @Input() isLoading = false;
  @Input() errorMessage = '';

  @Output() readonly remove = new EventEmitter<number>();
  @Output() readonly checkout = new EventEmitter<void>();

  protected itemCount(): number {
    return this.cart?.items.reduce((total, item) => total + item.quantity, 0) ?? 0;
  }

  protected totalAmount(): number {
    return this.cart?.totalAmount ?? 0;
  }

  protected trackItem(_: number, item: { bookId: number }): number {
    return item.bookId;
  }
}

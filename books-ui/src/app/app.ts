import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { BookApi, BookColumnMetadata, BookRecord } from './book-api';
import { Cart, CartApi } from './cart-api';
import { ShoppingCartComponent } from './shopping-cart.component';

@Component({
  selector: 'app-root',
  imports: [CommonModule, ReactiveFormsModule, ShoppingCartComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly bookApi = inject(BookApi);
  private readonly cartApi = inject(CartApi);

  protected readonly columns = signal<BookColumnMetadata[]>([]);
  protected readonly books = signal<BookRecord[]>([]);
  protected readonly cart = signal<Cart | null>(null);
  protected readonly isLoading = signal(true);
  protected readonly isCartLoading = signal(false);
  protected readonly isSaving = signal(false);
  protected readonly selectedBookId = signal<number | null>(null);
  protected readonly errorMessage = signal('');
  protected readonly cartErrorMessage = signal('');
  protected readonly searchableColumns = computed(() =>
    this.columns().filter((column) => !column.primaryKey)
  );

  protected readonly form = new FormGroup<Record<string, FormControl<string | null>>>({});

  constructor() {
    this.loadPage();
  }

  protected trackColumn(_: number, column: BookColumnMetadata): string {
    return column.name;
  }

  protected readonly trackBook = (_: number, book: BookRecord): unknown => this.primaryKeyValue(book);

  protected isPrimaryKey(column: BookColumnMetadata): boolean {
    return column.primaryKey;
  }

  protected isRequiredColumn(column: BookColumnMetadata): boolean {
    return !column.primaryKey && !column.nullable;
  }

  protected isNumberColumn(column: BookColumnMetadata): boolean {
    const type = column.jdbcType.toLowerCase();
    return ['int', 'serial', 'numeric', 'decimal', 'real', 'double', 'float'].some((token) =>
      type.includes(token)
    );
  }

  protected selectBook(book: BookRecord): void {
    this.selectedBookId.set(Number(this.primaryKeyValue(book)));
    this.errorMessage.set('');

    for (const column of this.searchableColumns()) {
      const value = book[column.name];
      this.form.controls[column.name]?.setValue(value == null ? '' : String(value));
    }
  }

  protected resetForm(): void {
    this.selectedBookId.set(null);
    this.errorMessage.set('');
    this.form.reset();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage.set('Fill in all required fields before saving.');
      return;
    }

    const payload = this.buildPayload();
    this.isSaving.set(true);
    this.errorMessage.set('');

    const request$ = this.selectedBookId() == null
      ? this.bookApi.createBook(payload)
      : this.bookApi.updateBook(this.selectedBookId()!, payload);

    request$
      .pipe(finalize(() => this.isSaving.set(false)))
      .subscribe({
        next: () => {
          this.resetForm();
          this.loadBooks();
        },
        error: (error: HttpErrorResponse) => {
          this.errorMessage.set(this.toSaveErrorMessage(error));
        }
      });
  }

  protected removeBook(book: BookRecord): void {
    const id = Number(this.primaryKeyValue(book));
    if (!Number.isFinite(id)) {
      this.errorMessage.set('Book id is invalid.');
      return;
    }

    this.errorMessage.set('');
    this.bookApi.deleteBook(id).subscribe({
      next: () => {
        if (this.selectedBookId() === id) {
          this.resetForm();
        }
        this.loadBooks();
      },
      error: () => {
        this.errorMessage.set('Delete failed.');
      }
    });
  }

  protected addToCart(book: BookRecord): void {
    const id = Number(this.primaryKeyValue(book));
    if (!Number.isFinite(id)) {
      this.cartErrorMessage.set('Book id is invalid.');
      return;
    }

    this.cartErrorMessage.set('');
    this.isCartLoading.set(true);
    this.cartApi.addItem({ bookId: id, quantity: 1 })
      .pipe(finalize(() => this.isCartLoading.set(false)))
      .subscribe({
      next: (cart) => {
        this.cart.set(cart);
      },
      error: () => {
        this.cartErrorMessage.set('Add to cart failed. Check cart-service availability.');
      }
    });
  }

  protected removeFromCart(bookId: number): void {
    this.cartErrorMessage.set('');
    this.isCartLoading.set(true);
    this.cartApi.removeItem(bookId)
      .pipe(finalize(() => this.isCartLoading.set(false)))
      .subscribe({
        next: (cart) => {
          this.cart.set(cart);
        },
        error: () => {
          this.cartErrorMessage.set('Remove from cart failed.');
        }
      });
  }

  protected checkoutCart(): void {
    this.cartErrorMessage.set('');
    this.isCartLoading.set(true);
    this.cartApi.checkout()
      .pipe(finalize(() => this.isCartLoading.set(false)))
      .subscribe({
        next: () => {
          this.loadCart();
        },
        error: () => {
          this.cartErrorMessage.set('Checkout failed.');
        }
      });
  }

  protected valueFor(book: BookRecord, columnName: string): string {
    const value = book[columnName];
    return value == null ? '' : String(value);
  }

  protected hasFieldError(columnName: string): boolean {
    const control = this.form.controls[columnName];
    return !!control && control.invalid && (control.dirty || control.touched);
  }

  protected fieldErrorMessage(column: BookColumnMetadata): string {
    const control = this.form.controls[column.name];
    if (!control || !control.errors) {
      return '';
    }

    if (control.errors['required']) {
      return `${column.name} is required.`;
    }

    return 'Invalid value.';
  }

  private loadPage(): void {
    this.loadCart();
    this.bookApi.getColumns().subscribe({
      next: (columns) => {
        this.columns.set(columns);
        this.buildForm(columns);
        this.loadBooks();
      },
      error: () => {
        this.isLoading.set(false);
        this.errorMessage.set('Unable to load book metadata from the backend.');
      }
    });
  }

  private loadBooks(): void {
    this.isLoading.set(true);
    this.bookApi.getBooks()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (books) => this.books.set(books),
        error: () => {
          this.errorMessage.set('Unable to load books.');
        }
      });
  }

  private loadCart(): void {
    this.isCartLoading.set(true);
    this.cartApi.getCart()
      .pipe(finalize(() => this.isCartLoading.set(false)))
      .subscribe({
        next: (cart) => {
          this.cart.set(cart);
          this.cartErrorMessage.set('');
        },
        error: () => {
          this.cartErrorMessage.set('Unable to load cart.');
        }
      });
  }

  private buildForm(columns: BookColumnMetadata[]): void {
    for (const column of columns) {
      if (!column.primaryKey) {
        this.form.addControl(
          column.name,
          new FormControl('', column.nullable ? [] : [Validators.required])
        );
      }
    }
  }

  private buildPayload(): BookRecord {
    const payload: BookRecord = {};
    for (const column of this.searchableColumns()) {
      const rawValue = this.form.controls[column.name]?.value ?? '';
      payload[column.name] = rawValue === '' ? null : this.castValue(column, rawValue);
    }
    return payload;
  }

  private castValue(column: BookColumnMetadata, value: string): string | number {
    return this.isNumberColumn(column) ? Number(value) : value;
  }

  private primaryKeyValue(book: BookRecord): unknown {
    return book[this.columns().find((column) => column.primaryKey)?.name ?? 'id'];
  }

  private toSaveErrorMessage(error: HttpErrorResponse): string {
    const backendMessage = this.extractBackendMessage(error.error);
    if (backendMessage) {
      return `Book save failed: ${backendMessage}`;
    }

    if (error.status === 0) {
      return 'Book save failed. Catalog service is unreachable.';
    }

    return `Book save failed with status ${error.status}.`;
  }

  private extractBackendMessage(errorBody: unknown): string | null {
    if (typeof errorBody === 'string' && errorBody.trim() !== '') {
      return errorBody.trim();
    }

    if (errorBody && typeof errorBody === 'object') {
      const message = (errorBody as { message?: unknown }).message;
      if (typeof message === 'string' && message.trim() !== '') {
        return message.trim();
      }

      const error = (errorBody as { error?: unknown }).error;
      if (typeof error === 'string' && error.trim() !== '') {
        return error.trim();
      }
    }

    return null;
  }
} // End of App component

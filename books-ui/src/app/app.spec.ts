import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { App } from './app';
import { BookApi } from './book-api';
import { CartApi } from './cart-api';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        {
          provide: BookApi,
          useValue: {
            getColumns: () => of([]),
            getBooks: () => of([]),
            createBook: () => of({}),
            updateBook: () => of({}),
            deleteBook: () => of(void 0)
          }
        },
        {
          provide: CartApi,
          useValue: {
            getCart: () => of({ cartId: '1', customerId: 'customer-100', items: [], totalAmount: 0 }),
            addItem: () => of({ cartId: '1', customerId: 'customer-100', items: [], totalAmount: 0 }),
            removeItem: () => of({ cartId: '1', customerId: 'customer-100', items: [], totalAmount: 0 }),
            checkout: () => of({ cartId: '1', customerId: 'customer-100', items: [], totalAmount: 0 })
          }
        }
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render title', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Manage the bookstore catalog');
  });
});

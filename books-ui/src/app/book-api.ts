import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface BookColumnMetadata {
  name: string;
  jdbcType: string;
  nullable: boolean;
  primaryKey: boolean;
}

export type BookRecord = Record<string, unknown>;

@Injectable({ providedIn: 'root' })
export class BookApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8081/api/books';

  getColumns(): Observable<BookColumnMetadata[]> {
    return this.http.get<BookColumnMetadata[]>(`${this.baseUrl}/metadata/columns`);
  }

  getBooks(): Observable<BookRecord[]> {
    return this.http.get<BookRecord[]>(this.baseUrl);
  }

  createBook(payload: BookRecord): Observable<BookRecord> {
    return this.http.post<BookRecord>(this.baseUrl, payload);
  }

  updateBook(bookId: number, payload: BookRecord): Observable<BookRecord> {
    return this.http.put<BookRecord>(`${this.baseUrl}/${bookId}`, payload);
  }

  deleteBook(bookId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${bookId}`);
  }
}

import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-payment-cancel-page',
  imports: [CommonModule, RouterLink],
  templateUrl: './payment-cancel-page.component.html',
  styleUrl: './payment-status-page.css'
})
export class PaymentCancelPageComponent {
}

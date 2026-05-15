import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './auth.guard';
import { DashboardComponent } from './dashboard.component';
import { LoginPageComponent } from './login-page.component';
import { PaymentCancelPageComponent } from './payment-cancel-page.component';
import { PaymentSuccessPageComponent } from './payment-success-page.component';
import { RegisterPageComponent } from './register-page.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'app' },
  { path: 'login', component: LoginPageComponent, canActivate: [guestGuard] },
  { path: 'register', component: RegisterPageComponent, canActivate: [guestGuard] },
  { path: 'payment/success', component: PaymentSuccessPageComponent, canActivate: [authGuard] },
  { path: 'payment/cancel', component: PaymentCancelPageComponent, canActivate: [authGuard] },
  { path: 'app', component: DashboardComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: 'app' }
];

import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './auth.guard';
import { DashboardComponent } from './dashboard.component';
import { LoginPageComponent } from './login-page.component';
import { RegisterPageComponent } from './register-page.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'app' },
  { path: 'login', component: LoginPageComponent, canActivate: [guestGuard] },
  { path: 'register', component: RegisterPageComponent, canActivate: [guestGuard] },
  { path: 'app', component: DashboardComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: 'app' }
];

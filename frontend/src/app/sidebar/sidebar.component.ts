import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.css'
})
export class SidebarComponent {
  navigationItems = [
    { number: '01', icon: 'DB', label: 'Dashboard', route: '/dashboard' },
    { number: '02', icon: 'DO', label: 'Documents', route: '/documents' },
    { number: '03', icon: 'QA', label: 'Ask Question', route: '/ask' },
    { number: '04', icon: 'CI', label: 'Results / Citations', route: '/results' },
    { number: '05', icon: 'PI', label: 'Pipeline Status', route: '/pipeline' },
    { number: '06', icon: 'TK', label: 'Token Usage', route: '/tokens' },
    { number: '07', icon: 'OB', label: 'Observability', route: '/observability' }
  ];
}

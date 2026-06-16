import { Component } from '@angular/core';

@Component({
  selector: 'app-header',
  standalone: true,
  templateUrl: './header.component.html',
  styleUrl: './header.component.css'
})
export class HeaderComponent {
  title = 'DocumentETL';
  subtitle = 'Document ingestion, embeddings, Q&A, and citations';
}

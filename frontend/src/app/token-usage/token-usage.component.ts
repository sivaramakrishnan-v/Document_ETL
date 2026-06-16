import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { DocumentEtlService, TokenUsageEvent, TokenUsageSummary } from '../document-etl.service';

@Component({
  selector: 'app-token-usage',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './token-usage.component.html',
  styleUrl: './token-usage.component.css'
})
export class TokenUsageComponent implements OnInit {
  summary: TokenUsageSummary | null = null;
  events: TokenUsageEvent[] = [];
  isLoading = false;
  message = '';

  constructor(private documentEtlService: DocumentEtlService) {}

  ngOnInit(): void {
    this.loadTokenUsage();
  }

  loadTokenUsage(): void {
    this.isLoading = true;
    this.message = 'Loading token usage metrics...';
    this.documentEtlService.getTokenUsageSummary().subscribe({
      next: (summary) => {
        this.summary = summary;
        this.message = 'Token usage metrics loaded from backend.';
        this.isLoading = false;
      },
      error: () => {
        this.summary = null;
        this.message = 'Token usage metrics are not available from backend yet.';
        this.isLoading = false;
      }
    });

    this.documentEtlService.getTokenUsageEvents().subscribe({
      next: (events) => this.events = events,
      error: () => this.events = []
    });
  }
}

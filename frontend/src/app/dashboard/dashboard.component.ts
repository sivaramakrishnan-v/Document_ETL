import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DocumentEtlService, PipelineStatusResponse, RagCheckpoint, UploadedDocument } from '../document-etl.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  documents: UploadedDocument[] = [];
  pipelineStatus: PipelineStatusResponse | null = null;
  checkpoints: RagCheckpoint[] = [];
  isLoading = false;
  errorMessage = '';

  constructor(private documentEtlService: DocumentEtlService) {}

  ngOnInit(): void {
    this.loadDashboard();
  }

  loadDashboard(): void {
    this.isLoading = true;
    this.errorMessage = '';

    this.documentEtlService.getDocuments().subscribe({
      next: (documents) => {
        this.documents = documents;
        this.finishIfReady();
      },
      error: () => {
        this.errorMessage = 'Some dashboard data is not available from backend yet.';
        this.finishIfReady();
      }
    });

    this.documentEtlService.getPipelineStatusRaw().subscribe({
      next: (status) => {
        this.pipelineStatus = status;
        this.finishIfReady();
      },
      error: () => {
        this.errorMessage = 'Some dashboard data is not available from backend yet.';
        this.finishIfReady();
      }
    });

    this.documentEtlService.getCheckpointHistory().subscribe({
      next: (checkpoints) => {
        this.checkpoints = checkpoints;
        this.finishIfReady();
      },
      error: () => {
        this.errorMessage = 'Some dashboard data is not available from backend yet.';
        this.finishIfReady();
      }
    });
  }

  get totalChunks(): number | null {
    return this.pipelineStatus?.embeddings?.['chunks'] ?? null;
  }

  get totalEmbeddings(): number | null {
    return this.pipelineStatus?.embeddings?.['embeddings'] ?? null;
  }

  get questionsAsked(): number {
    return this.checkpoints.length;
  }

  get latestPipelineStatus(): string {
    if (!this.pipelineStatus) {
      return 'N/A';
    }

    const failedEvents = this.pipelineStatus.events?.['failed'] || 0;
    const missingEmbeddings = this.pipelineStatus.embeddings?.['missingEmbeddings'] || 0;
    if (failedEvents) {
      return 'Attention needed';
    }
    if (missingEmbeddings) {
      return 'Processing';
    }
    return 'Ready';
  }

  private responses = 0;
  private finishIfReady(): void {
    this.responses++;
    if (this.responses >= 3) {
      this.isLoading = false;
      this.responses = 0;
    }
  }
}

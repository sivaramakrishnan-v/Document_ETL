import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { DocumentEtlService, PipelineStage } from '../document-etl.service';

@Component({
  selector: 'app-pipeline-status',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './pipeline-status.component.html',
  styleUrl: './pipeline-status.component.css'
})
export class PipelineStatusComponent implements OnInit {
  documentId = '';
  isLoading = false;
  message = '';
  stages: PipelineStage[] = [];
  lastRefresh = '';
  hasError = false;

  constructor(private documentEtlService: DocumentEtlService) {}

  ngOnInit(): void {
    this.loadPipelineStatus();
  }

  loadPipelineStatus(): void {
    this.isLoading = true;
    this.message = 'Loading pipeline status from Spring Boot...';
    this.hasError = false;

    this.documentEtlService.getPipelineStatus(this.documentId).subscribe({
      next: (stages) => {
        this.stages = stages;
        this.message = 'Pipeline status loaded from the backend.';
        this.lastRefresh = new Date().toLocaleString();
        this.isLoading = false;
      },
      error: () => {
        this.stages = [];
        this.message = 'Unable to load pipeline status. Make sure the Spring Boot API is running on localhost:8080.';
        this.hasError = true;
        this.isLoading = false;
      }
    });
  }

  stageClass(stage: PipelineStage): string {
    return stage.state;
  }

  stageLabel(stage: PipelineStage): string {
    if (stage.state === 'in-progress') {
      return 'In progress';
    }

    return stage.state.charAt(0).toUpperCase() + stage.state.slice(1);
  }
}

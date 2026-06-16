import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { DocumentEtlService, RagCheckpoint } from '../document-etl.service';

@Component({
  selector: 'app-agent-workflow',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './agent-workflow.component.html',
  styleUrl: './agent-workflow.component.css'
})
export class AgentWorkflowComponent implements OnInit {
  checkpoints: RagCheckpoint[] = [];
  selectedCheckpoint: RagCheckpoint | null = null;
  isLoading = false;
  message = '';

  constructor(private documentEtlService: DocumentEtlService) {}

  ngOnInit(): void {
    this.loadWorkflow();
  }

  loadWorkflow(): void {
    this.isLoading = true;
    this.message = 'Loading checkpoint workflow data...';
    this.documentEtlService.getCheckpointHistory().subscribe({
      next: (checkpoints) => {
        this.checkpoints = checkpoints;
        this.selectedCheckpoint = checkpoints[0] || null;
        this.message = checkpoints.length
          ? 'Workflow checkpoint data loaded from backend.'
          : 'Agent workflow details are not available from backend yet.';
        this.isLoading = false;
      },
      error: () => {
        this.checkpoints = [];
        this.selectedCheckpoint = null;
        this.message = 'Agent workflow details are not available from backend yet.';
        this.isLoading = false;
      }
    });
  }
}

import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DocumentEtlService, RagCheckpoint } from '../document-etl.service';

interface AgentDecision {
  agent: string;
  node: string;
  decision: string;
  detail: string;
  status: 'complete' | 'active' | 'warning';
}

@Component({
  selector: 'app-agent-workflow',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './agent-workflow.component.html',
  styleUrl: './agent-workflow.component.css'
})
export class AgentWorkflowComponent implements OnInit {
  checkpoints: RagCheckpoint[] = [];
  selectedCheckpoint: RagCheckpoint | null = null;
  isLoading = false;
  message = '';
  threadId = '';
  checkpointId = '';

  constructor(
    private documentEtlService: DocumentEtlService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((params) => {
      this.threadId = params.get('threadId') || '';
      this.checkpointId = params.get('checkpointId') || '';
      this.loadWorkflow();
    });
  }

  loadWorkflow(): void {
    this.isLoading = true;
    this.message = 'Loading agent decision history...';
    const request$ = this.threadId
      ? this.documentEtlService.getCheckpointHistoryForThread(this.threadId)
      : this.documentEtlService.getCheckpointHistory();

    request$.subscribe({
      next: (checkpoints) => {
        this.checkpoints = checkpoints;
        this.selectedCheckpoint = this.findInitialSelection(checkpoints);
        this.message = checkpoints.length
          ? 'Agent decision history loaded from backend checkpoints.'
          : 'No agent decision history found. Ask a question first to create a checkpoint.';
        this.isLoading = false;
      },
      error: () => {
        this.checkpoints = [];
        this.selectedCheckpoint = null;
        this.message = 'Unable to load agent decision history. Make sure the Spring Boot API is running.';
        this.isLoading = false;
      }
    });
  }

  selectCheckpoint(checkpoint: RagCheckpoint): void {
    this.selectedCheckpoint = checkpoint;
  }

  decisions(): AgentDecision[] {
    if (!this.selectedCheckpoint) {
      return [];
    }

    const visited = this.selectedCheckpoint.agentVisited || [];
    const feedback = this.selectedCheckpoint.agentFeedback || [];
    if (visited.length > 0) {
      return visited.map((node, index) => ({
        agent: this.agentLabel(node),
        node,
        decision: feedback[index] || 'Step completed.',
        detail: this.stepDetail(node),
        status: this.stepStatus(node)
      }));
    }

    return this.derivedDecisions(this.selectedCheckpoint);
  }

  recordTitle(checkpoint: RagCheckpoint): string {
    return checkpoint.userQuery || checkpoint.normalizedQuery || checkpoint.rewrittenQuery || checkpoint.checkpointId || 'Checkpoint';
  }

  formatDate(value?: string): string {
    return value ? new Date(value).toLocaleString() : 'No timestamp';
  }

  displayValue(value?: string | number | null): string {
    if (value === null || value === undefined || value === '') {
      return 'Not available';
    }
    return String(value);
  }

  statusClass(value?: string): string {
    const normalized = (value || '').toLowerCase();
    if (normalized.includes('failed') || normalized.includes('revise') || normalized.includes('no_evidence')) {
      return 'red';
    }
    if (normalized.includes('retry') || normalized.includes('started') || normalized.includes('planning')) {
      return 'yellow';
    }
    if (normalized.includes('completed') || normalized.includes('grounded') || normalized.includes('sufficient')) {
      return 'green';
    }
    return 'blue';
  }

  private findInitialSelection(checkpoints: RagCheckpoint[]): RagCheckpoint | null {
    if (!checkpoints.length) {
      return null;
    }
    if (!this.checkpointId) {
      return checkpoints[0];
    }
    return checkpoints.find(checkpoint => String(checkpoint.checkpointId) === this.checkpointId) || checkpoints[0];
  }

  private derivedDecisions(checkpoint: RagCheckpoint): AgentDecision[] {
    const decisions: AgentDecision[] = [
      {
        agent: 'Query normalizer',
        node: 'normalize_query',
        decision: checkpoint.normalizedQuery ? 'Normalized the user question.' : 'Started from the original question.',
        detail: checkpoint.normalizedQuery || checkpoint.userQuery || 'No normalized query persisted.',
        status: 'complete'
      },
      {
        agent: 'Query planner',
        node: 'query_planner',
        decision: checkpoint.rewrittenQuery ? 'Prepared the retrieval query plan.' : 'No rewritten query was persisted.',
        detail: checkpoint.rewrittenQuery || 'No planning detail persisted.',
        status: checkpoint.rewrittenQuery ? 'complete' : 'warning'
      },
      {
        agent: 'Retrieval agent',
        node: 'retrieval_agent',
        decision: checkpoint.retrievalStrategy || 'No retrieval strategy persisted.',
        detail: `${checkpoint.retrievedChunkIds?.length || 0} chunk(s) from ${checkpoint.retrievedDocumentIds?.length || 0} document(s).`,
        status: checkpoint.retrievedChunkIds?.length ? 'complete' : 'warning'
      },
      {
        agent: 'Answer generator',
        node: 'answer_generator',
        decision: checkpoint.generatedAnswer ? 'Generated an answer from selected evidence.' : 'No generated answer persisted.',
        detail: checkpoint.generatedAnswer ? this.preview(checkpoint.generatedAnswer) : 'No answer text persisted.',
        status: checkpoint.generatedAnswer ? 'complete' : 'warning'
      },
      {
        agent: 'Answer validator',
        node: 'answer_validator',
        decision: checkpoint.validationStatus || 'No validation decision persisted.',
        detail: 'Validation checks whether the generated answer is supported by retrieved evidence.',
        status: checkpoint.validationStatus ? this.stepStatus(checkpoint.validationStatus) : 'warning'
      },
      {
        agent: 'Grounding scorer',
        node: 'grounding_scorer',
        decision: checkpoint.groundingStatus || 'No grounding score persisted.',
        detail: `Groundedness ${this.formatPercent(checkpoint.groundednessScore)}; citation coverage ${this.formatPercent(checkpoint.citationCoverageScore)}.`,
        status: checkpoint.groundingStatus ? this.stepStatus(checkpoint.groundingStatus) : 'warning'
      }
    ];

    return decisions;
  }

  private agentLabel(node: string): string {
    const labels: Record<string, string> = {
      normalize_query: 'Query normalizer',
      query_planner: 'Query planner',
      retrieval_agent: 'Retrieval agent',
      context_grader: 'Context grader',
      rewrite_query: 'Query rewriter',
      answer_generator: 'Answer generator',
      answer_validator: 'Answer validator',
      insufficient_evidence: 'Evidence fallback',
      persist_trace: 'Trace persister'
    };
    return labels[node] || this.titleize(node);
  }

  private stepDetail(node: string): string {
    const checkpoint = this.selectedCheckpoint;
    if (!checkpoint) {
      return '';
    }
    switch (node) {
      case 'normalize_query':
        return checkpoint.normalizedQuery || checkpoint.userQuery || 'No query text persisted.';
      case 'query_planner':
      case 'rewrite_query':
        return checkpoint.rewrittenQuery || checkpoint.normalizedQuery || 'No query plan text persisted.';
      case 'retrieval_agent':
        return `${checkpoint.retrievalStrategy || 'Retrieval strategy unavailable'}; ${checkpoint.retrievedChunkIds?.length || 0} chunk(s) selected.`;
      case 'context_grader':
        return `${checkpoint.retrievedContextSnapshot?.length || 0} evidence snippet(s) available for grading.`;
      case 'answer_generator':
        return checkpoint.generatedAnswer ? this.preview(checkpoint.generatedAnswer) : 'No generated answer persisted.';
      case 'answer_validator':
        return checkpoint.validationStatus || 'No validation status persisted.';
      case 'persist_trace':
        return checkpoint.workflowStatus || 'No workflow status persisted.';
      default:
        return checkpoint.workflowStatus || 'Checkpoint detail unavailable.';
    }
  }

  private stepStatus(value: string): 'complete' | 'active' | 'warning' {
    const normalized = value.toLowerCase();
    if (normalized.includes('retry') || normalized.includes('started') || normalized.includes('planning')) {
      return 'active';
    }
    if (normalized.includes('revise') || normalized.includes('failed') || normalized.includes('insufficient') || normalized.includes('no_evidence')) {
      return 'warning';
    }
    return 'complete';
  }

  private preview(value: string): string {
    return value.length > 180 ? `${value.slice(0, 180)}...` : value;
  }

  private formatPercent(value?: number): string {
    if (value === null || value === undefined) {
      return 'Not available';
    }
    const normalized = value <= 1 ? value * 100 : value;
    return `${Math.round(normalized)}%`;
  }

  private titleize(value: string): string {
    return value
      .replace(/[_-]+/g, ' ')
      .replace(/\b\w/g, letter => letter.toUpperCase());
  }
}

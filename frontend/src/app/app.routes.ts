import { Routes } from '@angular/router';
import { AgentWorkflowComponent } from './agent-workflow/agent-workflow.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { DocumentListComponent } from './document-list/document-list.component';
import { ObservabilityComponent } from './observability/observability.component';
import { PipelineStatusComponent } from './pipeline-status/pipeline-status.component';
import { QuestionAnswerComponent } from './question-answer/question-answer.component';
import { ResultsComponent } from './results/results.component';
import { TokenUsageComponent } from './token-usage/token-usage.component';
import { UploadComponent } from './upload/upload.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    component: DashboardComponent,
    data: { title: 'Dashboard', subtitle: 'Enterprise GenAI operations overview.' }
  },
  {
    path: 'upload',
    component: UploadComponent,
    data: { title: 'Upload Document', subtitle: 'Send a source document to the ingestion pipeline.' }
  },
  {
    path: 'documents',
    component: DocumentListComponent,
    data: { title: 'Documents', subtitle: 'Review uploaded document metadata and processing state.' }
  },
  {
    path: 'pipeline',
    component: PipelineStatusComponent,
    data: { title: 'Pipeline Status', subtitle: 'Track parsing, chunking, embedding, and vector storage.' }
  },
  {
    path: 'ask',
    component: QuestionAnswerComponent,
    data: { title: 'Ask Question', subtitle: 'Ask questions against uploaded and indexed documents.', compactHeader: true }
  },
  {
    path: 'results',
    component: ResultsComponent,
    data: { title: 'Results & Citations', subtitle: 'Review grounded answers and source chunks.' }
  },
  {
    path: 'agents',
    component: AgentWorkflowComponent,
    data: { title: 'Agent Decisions', subtitle: 'Review agent steps and decisions for each generated answer.' }
  },
  {
    path: 'tokens',
    component: TokenUsageComponent,
    data: { title: 'Token Usage', subtitle: 'Monitor model token consumption.' }
  },
  {
    path: 'observability',
    component: ObservabilityComponent,
    data: { title: 'Observability', subtitle: 'Latency, errors, and platform telemetry.' }
  },
  { path: '**', redirectTo: 'dashboard' }
];

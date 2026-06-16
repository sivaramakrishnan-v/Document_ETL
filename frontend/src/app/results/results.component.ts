import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Citation, DocumentEtlService, RagCheckpoint } from '../document-etl.service';

interface CitationView {
  label: string;
  source: string;
  chunkId: string;
  documentId: string;
  page: string;
  score: number | null;
  text: string;
}

interface ChunkView {
  key: string;
  title: string;
  chunkId: string;
  documentId: string;
  page: string;
  score: number | null;
  text: string;
}

interface SourceView {
  documentId: string;
  label: string;
  chunkCount: number;
}

interface AnswerPart {
  text: string;
  citationId?: string;
}

@Component({
  selector: 'app-results',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './results.component.html',
  styleUrl: './results.component.css'
})
export class ResultsComponent implements OnInit {
  groundingScore = 0;
  message = '';
  citations: Citation[] = [];
  answer = '';
  question = '';
  createdAt = '';
  groundingStatus = '';
  workflowStatus = '';
  isLoading = false;
  threadId = '';
  checkpoints: RagCheckpoint[] = [];
  selectedCheckpoint: RagCheckpoint | null = null;
  citationCoverageScore = 0;
  unsupportedClaimsCount: number | null = null;
  retrievedChunks: string[] = [];
  retrievedChunkIds: string[] = [];
  retrievedDocumentIds: number[] = [];
  readonly pageSize = 15;
  currentPage = 1;
  expandedChunks = new Set<string>();

  constructor(private documentEtlService: DocumentEtlService) {}

  ngOnInit(): void {
    this.loadResults();
  }

  loadResults(): void {
    this.isLoading = true;
    this.message = 'Loading persisted grounding history from Spring Boot...';

    this.documentEtlService.getCheckpointHistory().subscribe({
      next: (checkpoints) => {
        this.checkpoints = checkpoints;
        this.currentPage = 1;
        this.selectCheckpoint(checkpoints[0] || null);
        this.message = checkpoints.length
          ? `Loaded ${checkpoints.length} persisted checkpoint record(s) from the database.`
          : 'No saved RAG results found yet. Ask a question first to create citations.';
        this.isLoading = false;
      },
      error: () => {
        this.clearSelection();
        this.checkpoints = [];
        this.message = 'Unable to load results. Make sure the Spring Boot API is running on localhost:8080.';
        this.isLoading = false;
      }
    });
  }

  loadThreadHistory(): void {
    const value = this.threadId.trim();
    if (!value) {
      this.message = 'Enter a thread ID before loading history.';
      return;
    }

    this.isLoading = true;
    this.message = `Loading checkpoint history for ${value}...`;

    this.documentEtlService.getCheckpointHistoryForThread(value).subscribe({
      next: (checkpoints) => {
        this.checkpoints = checkpoints;
        this.currentPage = 1;
        this.selectCheckpoint(checkpoints[0] || null);
        this.message = checkpoints.length
          ? `Loaded ${checkpoints.length} checkpoint record(s) for ${value}.`
          : `No checkpoint records found for ${value}.`;
        this.isLoading = false;
      },
      error: () => {
        this.message = `Unable to load checkpoint history for ${value}.`;
        this.isLoading = false;
      }
    });
  }

  selectCheckpoint(checkpoint: RagCheckpoint | null): void {
    this.selectedCheckpoint = checkpoint;
    this.expandedChunks.clear();
    if (!checkpoint) {
      this.clearSelection();
      return;
    }

    const response = this.documentEtlService.mapCheckpointToResult(checkpoint);
    this.answer = response.answer;
    this.question = response.question || '';
    this.createdAt = response.createdAt || '';
    this.groundingStatus = response.groundingStatus || '';
    this.workflowStatus = response.workflowStatus || '';
    this.groundingScore = response.groundingScore;
    this.citations = response.citations;
    this.threadId = checkpoint.threadId || this.threadId;
    this.citationCoverageScore = this.toPercent(checkpoint.citationCoverageScore ?? 0);
    this.unsupportedClaimsCount = checkpoint.unsupportedClaimsCount ?? null;
    this.retrievedChunks = checkpoint.retrievedContextSnapshot || [];
    this.retrievedChunkIds = checkpoint.retrievedChunkIds || [];
    this.retrievedDocumentIds = checkpoint.retrievedDocumentIds || [];
  }

  paginatedCheckpoints(): RagCheckpoint[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.checkpoints.slice(start, start + this.pageSize);
  }

  totalPages(): number {
    return Math.max(1, Math.ceil(this.checkpoints.length / this.pageSize));
  }

  goToPage(page: number): void {
    this.currentPage = Math.min(Math.max(page, 1), this.totalPages());
  }

  recordTitle(checkpoint: RagCheckpoint): string {
    return checkpoint.userQuery || checkpoint.normalizedQuery || checkpoint.rewrittenQuery || checkpoint.checkpointId || 'Checkpoint';
  }

  evidenceStatus(): string {
    return this.groundingStatus || this.selectedCheckpoint?.validationStatus || 'N/A';
  }

  evidenceStatusDescription(): string {
    const chunks = this.chunkViews().length;
    const sources = this.sourceViews().length;
    if (!this.selectedCheckpoint) {
      return 'Select a checkpoint to review evidence support.';
    }
    if (!chunks && !sources) {
      return 'No persisted retrieved evidence is available for this checkpoint.';
    }
    return `Answer supported by ${chunks || 'N/A'} retrieved chunk${chunks === 1 ? '' : 's'} from ${sources || 'N/A'} source document${sources === 1 ? '' : 's'}.`;
  }

  answerParts(): AnswerPart[] {
    const value = this.answer || '';
    const parts: AnswerPart[] = [];
    const pattern = /\[doc=(\d+)]/gi;
    let lastIndex = 0;
    let match: RegExpExecArray | null;
    while ((match = pattern.exec(value)) !== null) {
      if (match.index > lastIndex) {
        parts.push({ text: value.slice(lastIndex, match.index) });
      }
      parts.push({ text: `[${match[1]}]`, citationId: match[1] });
      lastIndex = match.index + match[0].length;
    }
    if (lastIndex < value.length) {
      parts.push({ text: value.slice(lastIndex) });
    }
    return parts.length ? parts : [{ text: value || 'N/A' }];
  }

  citationViews(): CitationView[] {
    return this.citations.map((citation, index) => {
      const documentId = this.documentIdFromSource(citation.sourceName) || String(this.retrievedDocumentIds[index] || '');
      const cleanSource = this.cleanCitationSource(citation.sourceName, documentId);
      return {
        label: `Citation ${index + 1}`,
        source: cleanSource || (documentId ? `Document ${documentId}` : 'Source unavailable'),
        chunkId: citation.chunkId || this.retrievedChunkIds[index] || 'N/A',
        documentId: documentId || 'N/A',
        page: citation.pageNumber && citation.pageNumber > 0 ? String(citation.pageNumber) : 'N/A',
        score: citation.score || this.citationCoverageScore || null,
        text: citation.text || 'No citation text available.'
      };
    });
  }

  sourceViews(): SourceView[] {
    const counts = new Map<string, number>();
    this.chunkViews().forEach((chunk) => {
      if (chunk.documentId !== 'N/A') {
        counts.set(chunk.documentId, (counts.get(chunk.documentId) || 0) + 1);
      }
    });
    this.citationViews().forEach((citation) => {
      if (citation.documentId !== 'N/A' && !counts.has(citation.documentId)) {
        counts.set(citation.documentId, 0);
      }
    });
    this.retrievedDocumentIds.forEach((documentId) => {
      const key = String(documentId);
      if (!counts.has(key)) {
        counts.set(key, 0);
      }
    });
    return Array.from(counts.entries()).map(([documentId, chunkCount]) => ({
      documentId,
      label: `Document ${documentId}`,
      chunkCount
    }));
  }

  chunkViews(): ChunkView[] {
    const rowCount = Math.max(this.retrievedChunks.length, this.retrievedChunkIds.length, this.retrievedDocumentIds.length);
    return Array.from({ length: rowCount }, (_, index) => {
      const rawText = this.retrievedChunks[index] || '';
      const parsed = this.parseEvidenceText(rawText);
      const documentId = parsed.documentId || String(this.retrievedDocumentIds[index] || '');
      const chunkId = this.retrievedChunkIds[index] || parsed.chunkId || `chunk-${index + 1}`;
      return {
        key: `${chunkId}-${index}`,
        title: `Chunk ${index + 1}`,
        chunkId,
        documentId: documentId || 'N/A',
        page: parsed.page || 'N/A',
        score: parsed.score,
        text: parsed.text || rawText || 'No retrieved text saved for this chunk.'
      };
    });
  }

  toggleChunk(key: string): void {
    if (this.expandedChunks.has(key)) {
      this.expandedChunks.delete(key);
      return;
    }
    this.expandedChunks.add(key);
  }

  isChunkExpanded(key: string): boolean {
    return this.expandedChunks.has(key);
  }

  chunkPreview(chunk: ChunkView): string {
    if (this.isChunkExpanded(chunk.key) || chunk.text.length <= 420) {
      return chunk.text;
    }
    return `${chunk.text.slice(0, 420).trim()}...`;
  }

  hasChunkOverflow(chunk: ChunkView): boolean {
    return chunk.text.length > 420;
  }

  confidenceExplanation(): string {
    if (!this.selectedCheckpoint) {
      return 'Select a checkpoint to review groundedness and citation coverage.';
    }
    return `Grounding status is ${this.displayValue(this.groundingStatus)}. Citation coverage is ${this.formatPercent(this.citationCoverageScore)}, with ${this.displayValue(this.unsupportedClaimsCount)} unsupported claims reported by the workflow.`;
  }

  statusClass(status: string | null | undefined): string {
    const normalized = (status || '').toUpperCase();
    if (normalized === 'GROUNDED') {
      return 'green';
    }
    if (normalized === 'WEAKLY_GROUNDED') {
      return 'yellow';
    }
    if (normalized === 'UNSUPPORTED' || normalized === 'FAILED' || normalized === 'REVISE') {
      return 'red';
    }
    if (normalized === 'COMPLETED' || normalized === 'SUCCESS') {
      return 'blue';
    }
    return 'neutral';
  }

  formatDate(value: string | undefined | null): string {
    if (!value) {
      return 'N/A';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(date);
  }

  formatPercent(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return 'N/A';
    }
    return `${this.toPercent(value).toFixed(1)}%`;
  }

  formatScore(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return 'N/A';
    }
    return value > 1 ? `${value.toFixed(1)}%` : value.toFixed(4);
  }

  scorePercent(value: number | null | undefined): number {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return 0;
    }
    return Math.max(0, Math.min(100, value <= 1 ? value * 100 : value));
  }

  displayValue(value: string | number | null | undefined): string {
    if (value === null || value === undefined || value === '') {
      return 'N/A';
    }
    return String(value);
  }

  private clearSelection(): void {
    this.selectedCheckpoint = null;
    this.answer = '';
    this.question = '';
    this.createdAt = '';
    this.groundingStatus = '';
    this.workflowStatus = '';
    this.groundingScore = 0;
    this.citationCoverageScore = 0;
    this.unsupportedClaimsCount = null;
    this.citations = [];
    this.retrievedChunks = [];
    this.retrievedChunkIds = [];
    this.retrievedDocumentIds = [];
  }

  private toPercent(value: number): number {
    if (!value) {
      return 0;
    }
    return value <= 1 ? value * 100 : value;
  }

  private documentIdFromSource(source: string): string {
    const match = /\[?doc=(\d+)]?/i.exec(source || '');
    return match ? match[1] : '';
  }

  private cleanCitationSource(source: string, documentId: string): string {
    const value = (source || '').trim();
    if (!value || /^\[?doc=\d+]$/i.test(value)) {
      return documentId ? `Document ${documentId}` : '';
    }
    return value.replace(/^\[|\]$/g, '');
  }

  private parseEvidenceText(raw: string): { documentId: string; chunkId: string; page: string; score: number | null; text: string } {
    const documentId = /doc=(\d+)/i.exec(raw || '')?.[1] || '';
    const chunkId = /chunk=([^\s]+)/i.exec(raw || '')?.[1] || '';
    const page = /page=([^\s]+)/i.exec(raw || '')?.[1] || '';
    const scoreText = /score=([0-9.]+)/i.exec(raw || '')?.[1];
    const text = /text=(.*)$/is.exec(raw || '')?.[1]?.trim() || raw;
    return {
      documentId,
      chunkId,
      page,
      score: scoreText ? Number(scoreText) : null,
      text
    };
  }
}

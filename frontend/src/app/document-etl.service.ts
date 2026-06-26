import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';

export type DocumentStatus = 'Ready' | 'Processing' | 'Chunked' | 'Failed';
export type PipelineStageState = 'completed' | 'in-progress' | 'pending';

export interface UploadedDocument {
  id: number;
  fileName: string;
  fileType: string;
  uploadedAt: string;
  status: DocumentStatus;
  chunkCount: number;
  embeddingCount?: number | null;
}

export interface PipelineStage {
  name: string;
  description: string;
  state: PipelineStageState;
}

export interface Citation {
  sourceName: string;
  chunkId: string;
  pageNumber: number;
  score: number;
  text: string;
}

export interface AskQuestionResponse {
  answer: string;
  groundingScore: number;
  citations: Citation[];
  question?: string;
  createdAt?: string;
  groundingStatus?: string;
  workflowStatus?: string;
  threadId?: string;
  checkpointId?: string;
  validationStatus?: string;
  contextGrade?: string;
  validationOutcome?: string;
  citationCoverageScore?: number;
  unsupportedClaimsCount?: number;
  retrievedDocuments?: string[];
  retrievedChunks?: string[];
  visited?: string[];
  feedback?: string[];
}

export interface RagCheckpoint {
  checkpointId: string;
  threadId: string;
  userQuery?: string;
  normalizedQuery?: string;
  rewrittenQuery?: string;
  retrievalStrategy?: string;
  retrievedDocumentIds?: number[];
  retrievedChunkIds?: string[];
  retrievedContextSnapshot?: string[];
  generatedAnswer?: string;
  citations?: string[];
  agentVisited?: string[];
  agentFeedback?: string[];
  validationStatus?: string;
  groundednessScore?: number;
  citationCoverageScore?: number;
  unsupportedClaimsCount?: number;
  groundingStatus?: string;
  workflowStatus?: string;
  errorMessage?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PipelineStatusResponse {
  documents?: Record<string, number>;
  embeddings?: Record<string, number>;
  embeddingJobs?: Record<string, number>;
  events?: Record<string, number>;
  recentEvents?: PipelineEventSummary[];
}

export interface PipelineEventSummary {
  eventType?: string;
  topicName?: string;
  documentId?: string;
  chunkId?: string;
  processingStatus?: string;
  errorMessage?: string;
  processedAt?: string;
}

export interface TokenUsageOperation {
  operationName: string;
  requestCount: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
}

export interface TokenUsageSummary {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  requestCount: number;
  successCount: number;
  failureCount: number;
  failureRatePct: number;
  firstSeenAt?: string;
  lastSeenAt?: string;
  topOperations?: TokenUsageOperation[];
}

export interface TokenUsageEvent {
  id: number;
  runId?: string;
  operationName?: string;
  modelName?: string;
  promptChars: number;
  completionChars: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  status?: string;
  errorMessage?: string;
  createdAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class DocumentEtlService {
  private readonly apiBaseUrl = '';

  private readonly endpoints = {
    uploadDocument: `${this.apiBaseUrl}/api/documents/upload`,
    getDocuments: `${this.apiBaseUrl}/api/documents`,
    getPipelineStatus: `${this.apiBaseUrl}/api/etl/v2/status`,
    askQuestion: `${this.apiBaseUrl}/api/chat/agent/ask`,
    getResults: `${this.apiBaseUrl}/api/dashboard/checkpoints?limit=1`,
    getCheckpointHistory: `${this.apiBaseUrl}/api/dashboard/checkpoints?limit=100`,
    getTokenSummary: `${this.apiBaseUrl}/api/tokens/summary?topOperations=10`,
    getTokenEvents: `${this.apiBaseUrl}/api/tokens/events?limit=50`
  };

  constructor(private http: HttpClient) {}

  uploadDocument(file: File): Observable<unknown> {
    return this.uploadDocuments([file]);
  }

  uploadDocuments(files: File[]): Observable<unknown> {
    const formData = new FormData();
    files.forEach((file) => formData.append('files', file));

    return this.http.post(this.endpoints.uploadDocument, formData);
  }

  getDocuments(): Observable<UploadedDocument[]> {
    return this.http.get<UploadedDocument[]>(this.endpoints.getDocuments);
  }

  getPipelineStatusRaw(): Observable<PipelineStatusResponse> {
    return this.http.get<PipelineStatusResponse>(this.endpoints.getPipelineStatus);
  }

  getPipelineStatus(documentId: string): Observable<PipelineStage[]> {
    return this.http.get<Record<string, any>>(this.endpoints.getPipelineStatus).pipe(
      map((status) => this.mapPipelineStatus(status, documentId))
    );
  }

  askQuestion(question: string, threadId: string | null): Observable<AskQuestionResponse> {
    const payload = { question, threadId };
    return this.http.post<Record<string, any>>(this.endpoints.askQuestion, payload).pipe(
      map((response) => this.mapAskResponse(response))
    );
  }

  getResults(): Observable<AskQuestionResponse> {
    return this.http.get<any[]>(this.endpoints.getResults).pipe(
      map((history) => this.mapCheckpointResult(history))
    );
  }

  getCheckpointHistory(): Observable<RagCheckpoint[]> {
    return this.http.get<RagCheckpoint[]>(this.endpoints.getCheckpointHistory);
  }

  getCheckpointHistoryForThread(threadId: string): Observable<RagCheckpoint[]> {
    return this.http.get<RagCheckpoint[]>(`${this.apiBaseUrl}/api/rag/checkpoints/${encodeURIComponent(threadId)}/history`);
  }

  getTokenUsageSummary(): Observable<TokenUsageSummary> {
    return this.http.get<TokenUsageSummary>(this.endpoints.getTokenSummary);
  }

  getTokenUsageEvents(): Observable<TokenUsageEvent[]> {
    return this.http.get<TokenUsageEvent[]>(this.endpoints.getTokenEvents);
  }

  mapCheckpointToResult(checkpoint: RagCheckpoint | null): AskQuestionResponse {
    if (!checkpoint) {
      return {
        answer: '',
        groundingScore: 0,
        citations: []
      };
    }

    return {
      answer: checkpoint.generatedAnswer || '',
      groundingScore: this.toPercent(checkpoint.groundednessScore ?? 0),
      citations: this.mapCheckpointCitations(checkpoint as Record<string, any>),
      question: checkpoint.userQuery || checkpoint.normalizedQuery || checkpoint.rewrittenQuery || '',
      createdAt: checkpoint.createdAt || '',
      groundingStatus: checkpoint.groundingStatus || checkpoint.validationStatus || '',
      workflowStatus: checkpoint.workflowStatus || '',
      threadId: checkpoint.threadId || '',
      checkpointId: checkpoint.checkpointId || '',
      validationStatus: checkpoint.validationStatus || '',
      citationCoverageScore: this.toPercent(checkpoint.citationCoverageScore ?? 0),
      unsupportedClaimsCount: checkpoint.unsupportedClaimsCount ?? undefined,
      retrievedDocuments: (checkpoint.retrievedDocumentIds || []).map(String),
      retrievedChunks: checkpoint.retrievedChunkIds || []
    };
  }

  private mapPipelineStatus(status: Record<string, any>, documentId: string): PipelineStage[] {
    const documents = status['documents'] || {};
    const embeddings = status['embeddings'] || {};
    const events = status['events'] || {};

    return [
      {
        name: 'Documents Received',
        description: documents.total
          ? `${documents.total} source document(s) staged in the backend.`
          : 'No source documents have been staged yet.',
        state: documents.total ? 'completed' : 'pending'
      },
      {
        name: 'Parsing',
        description: `${documents.completed || 0} completed, ${documents.chunked || 0} chunked, ${documents.failed || 0} failed.`,
        state: documents.completed || documents.chunked ? 'completed' : 'pending'
      },
      {
        name: 'Chunking',
        description: embeddings.chunks
          ? `${embeddings.chunks} text chunk(s) available for retrieval.`
          : 'No text chunks have been created yet.',
        state: embeddings.chunks ? 'completed' : 'pending'
      },
      {
        name: 'Embedding',
        description: `${embeddings.embeddings || 0} embedding(s), ${embeddings.missingEmbeddings || 0} chunk(s) still missing embeddings.`,
        state: embeddings.missingEmbeddings ? 'in-progress' : embeddings.embeddings ? 'completed' : 'pending'
      },
      {
        name: 'Vector storage',
        description: embeddings.embeddings
          ? 'Embeddings are stored and available to semantic search.'
          : 'No stored embeddings are available yet.',
        state: embeddings.embeddings && !embeddings.missingEmbeddings ? 'completed' : 'pending'
      },
      {
        name: 'Ready For Questions',
        description: events.failed
          ? `${events.failed} pipeline event(s) failed. Check backend logs before Q&A.`
          : 'Q&A can use documents after chunking and embedding complete.',
        state: events.failed ? 'pending' : embeddings.embeddings && !embeddings.missingEmbeddings ? 'completed' : 'pending'
      }
    ];
  }

  private mapAskResponse(response: Record<string, any>): AskQuestionResponse {
    return {
      answer: response['answer'] || response['response'] || response['finalAnswer'] || 'The backend returned a response without an answer field.',
      groundingScore: this.toPercent(response['groundednessScore'] ?? response['groundingScore'] ?? response['confidenceScore'] ?? 0),
      citations: this.mapCitations(response['citations'] || response['sources'] || response['retrievedChunks'] || []),
      groundingStatus: response['groundingStatus'] || '',
      workflowStatus: response['workflowStatus'] || '',
      threadId: response['threadId'] || '',
      checkpointId: response['checkpointId'] || '',
      validationStatus: response['validationStatus'] || '',
      contextGrade: response['contextGrade'] || '',
      validationOutcome: response['validationOutcome'] || '',
      citationCoverageScore: this.toPercent(response['citationCoverageScore'] ?? 0),
      unsupportedClaimsCount: response['unsupportedClaimsCount'],
      retrievedDocuments: Array.isArray(response['sources']) ? response['sources'].map(String) : [],
      retrievedChunks: Array.isArray(response['retrievedChunks']) ? response['retrievedChunks'].map(String) : [],
      visited: response['visited'] || [],
      feedback: response['feedback'] || []
    };
  }

  private mapCheckpointResult(history: any[]): AskQuestionResponse {
    const latest = history && history.length > 0 ? history[0] : null;
    if (!latest) {
      return {
        answer: '',
        groundingScore: 0,
        citations: []
      };
    }

    return this.mapCheckpointToResult(latest);
  }

  private mapCitations(values: any[]): Citation[] {
    if (!Array.isArray(values) || values.length === 0) {
      return [];
    }

    return values.map((value, index) => ({
      sourceName: value.sourceName || value.documentName || value.source || 'Unknown source',
      chunkId: value.chunkId || value.id || `chunk-${index + 1}`,
      pageNumber: value.pageNumber || value.page || 1,
      score: Math.round((value.score || value.similarityScore || value.confidence || 0) * (value.score <= 1 ? 100 : 1)),
      text: value.text || value.chunkText || value.content || 'No source text returned.'
    }));
  }

  private mapCheckpointCitations(checkpoint: Record<string, any>): Citation[] {
    const citations = Array.isArray(checkpoint['citations']) ? checkpoint['citations'] : [];
    const contexts = Array.isArray(checkpoint['retrievedContextSnapshot']) ? checkpoint['retrievedContextSnapshot'] : [];
    const chunkIds = Array.isArray(checkpoint['retrievedChunkIds']) ? checkpoint['retrievedChunkIds'] : [];
    const documentIds = Array.isArray(checkpoint['retrievedDocumentIds']) ? checkpoint['retrievedDocumentIds'] : [];
    const coverageScore = this.toPercent(checkpoint['citationCoverageScore'] ?? checkpoint['groundednessScore'] ?? 0);

    const rowCount = Math.max(citations.length, contexts.length, chunkIds.length);
    if (rowCount === 0) {
      return [];
    }

    return Array.from({ length: rowCount }, (_, index) => ({
      sourceName: citations[index] || (documentIds[index] ? `Document ${documentIds[index]}` : 'Retrieved source'),
      chunkId: chunkIds[index] || `context-${index + 1}`,
      pageNumber: 1,
      score: coverageScore,
      text: contexts[index] || citations[index] || 'No retrieved context text was saved for this citation.'
    }));
  }

  private toPercent(value: number): number {
    if (!value) {
      return 0;
    }

    return Math.round(value <= 1 ? value * 100 : value);
  }
}

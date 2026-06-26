import { CommonModule } from '@angular/common';
import { AfterViewChecked, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AskQuestionResponse, DocumentEtlService, RagCheckpoint } from '../document-etl.service';
import { forkJoin } from 'rxjs';

interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
  response?: AskQuestionResponse;
}

@Component({
  selector: 'app-question-answer',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './question-answer.component.html',
  styleUrls: ['./question-answer.component.css']
})
export class QuestionAnswerComponent implements OnInit, AfterViewChecked {
  @ViewChild('chatViewport') private chatViewport?: ElementRef<HTMLDivElement>;

  question = '';
  isAsking = false;
  errorMessage = '';
  totalDocuments = 0;
  messages: ChatMessage[] = [];
  threadId: string | null = null;

  private readonly threadIdStorageKey = 'chatThreadId';
  private shouldAutoScroll = false;

  suggestedQuestions = [
    'What is an ETF?',
    'What is hedging?',
    'Explain FICO score.',
    'What factors affect a credit score?'
  ];

  constructor(private documentEtlService: DocumentEtlService) {}

  ngOnInit(): void {
    this.loadInitialData();
  }

  ngAfterViewChecked(): void {
    if (this.shouldAutoScroll) {
      this.scrollChatToBottom();
      this.shouldAutoScroll = false;
    }
  }

  loadInitialData(): void {
    this.threadId = localStorage.getItem(this.threadIdStorageKey);

    const documents$ = this.documentEtlService.getDocuments();
    const history$ = this.threadId
      ? this.documentEtlService.getCheckpointHistoryForThread(this.threadId)
      : [];

    forkJoin([documents$, history$]).subscribe({
      next: ([documents, history]) => {
        this.totalDocuments = documents.length;
        if (history && history.length > 0) {
          this.messages = this.mapHistoryToMessages(history as RagCheckpoint[]);
          this.queueScrollToBottom();
        }
      },
      error: (err) => {
        console.error('Error loading initial data:', err);
        this.totalDocuments = 0;
        this.messages = [];
      }
    });
  }

  askQuestion(): void {
    if (!this.question.trim()) {
      return;
    }

    const askedQuestion = this.question.trim();
    this.isAsking = true;
    this.errorMessage = '';
    this.messages.push({ role: 'user', text: askedQuestion });
    this.question = '';
    this.queueScrollToBottom();

    this.documentEtlService.askQuestion(askedQuestion, this.threadId).subscribe({
      next: (response) => {
        this.messages.push({
          role: 'assistant',
          text: response.answer,
          response: response
        });
        if (response.threadId && !this.threadId) {
          this.threadId = response.threadId;
          localStorage.setItem(this.threadIdStorageKey, this.threadId);
        }
        this.isAsking = false;
        this.queueScrollToBottom();
      },
      error: () => {
        this.errorMessage = 'Unable to ask question. Make sure the Spring Boot API is running and documents are indexed.';
        this.isAsking = false;
        this.queueScrollToBottom();
      }
    });
  }

  startNewChat(): void {
    this.threadId = null;
    this.messages = [];
    this.question = '';
    this.errorMessage = '';
    localStorage.removeItem(this.threadIdStorageKey);
    this.queueScrollToBottom();
  }

  populateQuestion(question: string): void {
    this.question = question;
  }

  handleComposerKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.askQuestion();
    }
  }

  private mapHistoryToMessages(history: RagCheckpoint[]): ChatMessage[] {
    const chatMessages: ChatMessage[] = [];
    // The history is returned in descending order, so we reverse it to show the conversation chronologically.
    history.slice().reverse().forEach(checkpoint => {
      if (checkpoint.userQuery) {
        chatMessages.push({ role: 'user', text: checkpoint.userQuery });
      }
      if (checkpoint.generatedAnswer) {
        const response = this.documentEtlService.mapCheckpointToResult(checkpoint);
        chatMessages.push({ role: 'assistant', text: response.answer, response });
      }
    });
    return chatMessages;
  }

  private queueScrollToBottom(): void {
    this.shouldAutoScroll = true;
  }

  private scrollChatToBottom(): void {
    const viewport = this.chatViewport?.nativeElement;
    if (!viewport) {
      return;
    }
    viewport.scrollTop = viewport.scrollHeight;
  }
}

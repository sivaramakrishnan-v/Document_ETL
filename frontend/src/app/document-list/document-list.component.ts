import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DocumentEtlService, DocumentStatus, UploadedDocument } from '../document-etl.service';

@Component({
  selector: 'app-document-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './document-list.component.html',
  styleUrl: './document-list.component.css'
})
export class DocumentListComponent implements OnInit {
  documents: UploadedDocument[] = [];
  isLoading = false;
  message = '';
  errorMessage = '';

  constructor(private documentEtlService: DocumentEtlService) {}

  ngOnInit(): void {
    this.loadDocuments();
  }

  loadDocuments(): void {
    this.isLoading = true;
    this.message = 'Loading documents from Spring Boot...';
    this.errorMessage = '';

    this.documentEtlService.getDocuments().subscribe({
      next: (documents) => {
        this.documents = documents;
        this.message = documents.length ? 'Documents loaded from backend.' : '';
        this.isLoading = false;
      },
      error: () => {
        this.documents = [];
        this.message = '';
        this.errorMessage = 'Document metadata is not available from backend yet.';
        this.isLoading = false;
      }
    });
  }

  statusClass(status: DocumentStatus): string {
    return status.toLowerCase();
  }
}

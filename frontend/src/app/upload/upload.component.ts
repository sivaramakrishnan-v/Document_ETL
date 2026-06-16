import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DocumentEtlService } from '../document-etl.service';

@Component({
  selector: 'app-upload',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './upload.component.html',
  styleUrl: './upload.component.css'
})
export class UploadComponent {
  selectedFiles: File[] = [];
  isUploading = false;
  uploadMessage = '';
  uploadResponse: unknown = null;

  constructor(private documentEtlService: DocumentEtlService) {}

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;

    if (!input.files || input.files.length === 0) {
      this.selectedFiles = [];
      return;
    }

    this.selectedFiles = Array.from(input.files);
    this.uploadMessage = '';
  }

  uploadDocument(): void {
    if (this.selectedFiles.length === 0) {
      this.uploadMessage = 'Please choose one or more files before uploading.';
      return;
    }

    this.isUploading = true;
    this.uploadMessage = this.selectedFiles.length === 1 ? 'Uploading document...' : `Uploading ${this.selectedFiles.length} documents...`;
    this.uploadResponse = null;

    this.documentEtlService.uploadDocuments(this.selectedFiles).subscribe({
      next: (response) => {
        this.uploadResponse = response;
        this.uploadMessage = this.selectedFiles.length === 1
          ? 'Document uploaded successfully. Track processing progress from the Pipeline Status page.'
          : 'Documents uploaded successfully. Track processing progress from the Pipeline Status page.';
        this.isUploading = false;
      },
      error: () => {
        this.uploadMessage = 'Upload failed. Make sure the Spring Boot API is running on localhost:8080.';
        this.isUploading = false;
      }
    });
  }
}

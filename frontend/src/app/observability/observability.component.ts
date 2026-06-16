import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';

@Component({
  selector: 'app-observability',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './observability.component.html',
  styleUrl: './observability.component.css'
})
export class ObservabilityComponent {
  metrics = [
    'Upload Latency',
    'Parsing Latency',
    'Chunking Latency',
    'Embedding Latency',
    'Retrieval Latency',
    'LLM Latency',
    'Error Count'
  ];
}

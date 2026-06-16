import { Component, Input } from '@angular/core';
import { Citation } from '../document-etl.service';

@Component({
  selector: 'app-citation-card',
  standalone: true,
  templateUrl: './citation-card.component.html',
  styleUrl: './citation-card.component.css'
})
export class CitationCardComponent {
  @Input({ required: true }) citation!: Citation;
}

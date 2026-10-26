import { Component, computed, inject, input } from '@angular/core';

import { ArtworkGallery } from './artwork-gallery';
import { ArtworkService } from './artwork.service';

/** Bilder eines Films; gespeichert wird nach Kodi-Konvention neben dem Film. */
@Component({
  selector: 'app-movie-artwork',
  imports: [ArtworkGallery],
  template: '<app-artwork-gallery [source]="source()" />',
})
export class MovieArtwork {
  private readonly artworkService = inject(ArtworkService);

  readonly movieId = input.required<number>();

  protected readonly source = computed(() => this.artworkService.forMovie(this.movieId()));
}

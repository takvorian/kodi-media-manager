import { HttpResponse } from '@angular/common/http';

const FILE_NAME = /filename="?([^";]+)"?/;

/** Saves a downloaded file under the name the server gives. */
export function saveDownload(response: HttpResponse<Blob>, fallbackName: string): void {
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const url = URL.createObjectURL(response.body ?? new Blob());
  const link = document.createElement('a');
  link.href = url;
  link.download = FILE_NAME.exec(disposition)?.[1] ?? fallbackName;
  link.click();
  URL.revokeObjectURL(url);
}

import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { YearlyReport } from '../models/report';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);

  yearly(year: number): Observable<YearlyReport> {
    return this.http.get<YearlyReport>(`${environment.apiUrl}/reports/yearly`, { params: { year } });
  }
}

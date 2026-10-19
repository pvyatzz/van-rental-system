package com.vanrental.backend.controller;

import com.vanrental.backend.dto.ReportResponse;
import com.vanrental.backend.exception.InvalidRequestException;
import com.vanrental.backend.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;

/** Admin only (SecurityConfig: everything not listed as public needs ROLE_ADMIN). */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** e.g. GET /api/reports/yearly?year=2026 (defaults to the current year) */
    @GetMapping("/yearly")
    public ReportResponse yearly(@RequestParam(required = false) Integer year) {
        int y = year != null ? year : Year.now().getValue();
        if (y < 2000 || y > 2100) {
            throw new InvalidRequestException("Year must be between 2000 and 2100");
        }
        return reportService.yearly(y);
    }
}

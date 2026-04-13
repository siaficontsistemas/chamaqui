package com.helpdesk.helpdesk.api.report;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.report.PersonalReportRowResponse;
import com.helpdesk.helpdesk.service.ReportService;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

	private final ReportService reportService;

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@GetMapping("/personal")
	public List<PersonalReportRowResponse> getPersonalReport(@RequestParam String email) {
		return reportService.getPersonalReport(email);
	}
}

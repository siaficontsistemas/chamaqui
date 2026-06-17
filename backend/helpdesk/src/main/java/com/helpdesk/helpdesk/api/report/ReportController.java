package com.helpdesk.helpdesk.api.report;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.report.PersonalReportRowResponse;
import com.helpdesk.helpdesk.service.AppSessionService;
import com.helpdesk.helpdesk.service.ReportService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

	private final ReportService reportService;
	private final AppSessionService appSessionService;

	public ReportController(ReportService reportService, AppSessionService appSessionService) {
		this.reportService = reportService;
		this.appSessionService = appSessionService;
	}

	@GetMapping("/personal")
	public List<PersonalReportRowResponse> getPersonalReport(HttpSession session) {
		return reportService.getPersonalReport(appSessionService.requireCurrentEmail(session));
	}
}

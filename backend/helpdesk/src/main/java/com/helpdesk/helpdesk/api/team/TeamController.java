package com.helpdesk.helpdesk.api.team;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.company.CompanyAdminInviteResponse;
import com.helpdesk.helpdesk.dto.company.CreateCompanyAdminInviteRequest;
import com.helpdesk.helpdesk.dto.team.InviteTeamMemberRequest;
import com.helpdesk.helpdesk.dto.team.RespondTeamInviteRequest;
import com.helpdesk.helpdesk.dto.team.TeamInviteResponse;
import com.helpdesk.helpdesk.dto.team.TeamMemberResponse;
import com.helpdesk.helpdesk.dto.team.UpdateMemberSectorsRequest;
import com.helpdesk.helpdesk.service.AppSessionService;
import com.helpdesk.helpdesk.service.CompanyAccessRequestService;
import com.helpdesk.helpdesk.service.TeamService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/team")
public class TeamController {

	private final TeamService teamService;
	private final CompanyAccessRequestService companyAccessRequestService;
	private final AppSessionService appSessionService;

	public TeamController(
		TeamService teamService,
		CompanyAccessRequestService companyAccessRequestService,
		AppSessionService appSessionService
	) {
		this.teamService = teamService;
		this.companyAccessRequestService = companyAccessRequestService;
		this.appSessionService = appSessionService;
	}

	@GetMapping("/members")
	public List<TeamMemberResponse> listMembers(HttpSession session) {
		return teamService.listMembers(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/invites")
	public List<TeamInviteResponse> listInvites() {
		return teamService.listInvites();
	}

	@GetMapping("/invites/received")
	public List<TeamInviteResponse> listReceivedInvites(HttpSession session) {
		return teamService.listReceivedInvites(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/invites/sent")
	public List<TeamInviteResponse> listSentInvites(HttpSession session) {
		return teamService.listSentInvites(appSessionService.requireCurrentEmail(session));
	}

	@PostMapping("/invites")
	@ResponseStatus(HttpStatus.CREATED)
	public TeamInviteResponse invite(@Valid @RequestBody InviteTeamMemberRequest request, HttpSession session) {
		return teamService.invite(
			new InviteTeamMemberRequest(
				request.documentNumber(),
				appSessionService.requireCurrentEmail(session),
				request.sectorIds()
			)
		);
	}

	@PostMapping("/company-invites")
	@ResponseStatus(HttpStatus.CREATED)
	public CompanyAdminInviteResponse createCompanyInvite(
		@Valid @RequestBody CreateCompanyAdminInviteRequest request,
		HttpSession session
	) {
		return companyAccessRequestService.createAdminInvite(
			new CreateCompanyAdminInviteRequest(
				request.fullName(),
				request.email(),
				request.documentNumber(),
				appSessionService.requireCurrentEmail(session)
			)
		);
	}

	@PostMapping("/invites/{inviteId}/accept")
	public TeamInviteResponse acceptInvite(
		@PathVariable UUID inviteId,
		@Valid @RequestBody RespondTeamInviteRequest request,
		HttpSession session
	) {
		return teamService.acceptInvite(inviteId, new RespondTeamInviteRequest(appSessionService.requireCurrentEmail(session)));
	}

	@PostMapping("/invites/{inviteId}/decline")
	public TeamInviteResponse declineInvite(
		@PathVariable UUID inviteId,
		@Valid @RequestBody RespondTeamInviteRequest request,
		HttpSession session
	) {
		return teamService.declineInvite(inviteId, new RespondTeamInviteRequest(appSessionService.requireCurrentEmail(session)));
	}

	@DeleteMapping("/invites/{inviteId}/notification")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteNotification(@PathVariable UUID inviteId, HttpSession session) {
		teamService.deleteNotification(inviteId, appSessionService.requireCurrentEmail(session));
	}

	@PutMapping("/members/{userId}/sectors")
	public List<TeamMemberResponse> updateMemberSectors(
		@PathVariable UUID userId,
		@Valid @RequestBody UpdateMemberSectorsRequest request,
		HttpSession session
	) {
		return teamService.updateMemberSectors(
			userId,
			new UpdateMemberSectorsRequest(appSessionService.requireCurrentEmail(session), request.sectorIds())
		);
	}

	@DeleteMapping("/members/{userId}")
	public List<TeamMemberResponse> removeMemberFromCompany(@PathVariable UUID userId, HttpSession session) {
		return teamService.removeMemberFromCompany(userId, appSessionService.requireCurrentEmail(session));
	}

	@DeleteMapping("/sectors/{sectorId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteSector(@PathVariable UUID sectorId, HttpSession session) {
		teamService.deleteSector(sectorId, appSessionService.requireCurrentEmail(session));
	}
}

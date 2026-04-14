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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.team.InviteTeamMemberRequest;
import com.helpdesk.helpdesk.dto.team.RespondTeamInviteRequest;
import com.helpdesk.helpdesk.dto.team.TeamInviteResponse;
import com.helpdesk.helpdesk.dto.team.TeamMemberResponse;
import com.helpdesk.helpdesk.dto.team.UpdateMemberSectorsRequest;
import com.helpdesk.helpdesk.service.TeamService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/team")
public class TeamController {

	private final TeamService teamService;

	public TeamController(TeamService teamService) {
		this.teamService = teamService;
	}

	@GetMapping("/members")
	public List<TeamMemberResponse> listMembers(@RequestParam(required = false) String email) {
		return teamService.listMembers(email);
	}

	@GetMapping("/invites")
	public List<TeamInviteResponse> listInvites() {
		return teamService.listInvites();
	}

	@GetMapping("/invites/received")
	public List<TeamInviteResponse> listReceivedInvites(@RequestParam String email) {
		return teamService.listReceivedInvites(email);
	}

	@GetMapping("/invites/sent")
	public List<TeamInviteResponse> listSentInvites(@RequestParam String email) {
		return teamService.listSentInvites(email);
	}

	@PostMapping("/invites")
	@ResponseStatus(HttpStatus.CREATED)
	public TeamInviteResponse invite(@Valid @RequestBody InviteTeamMemberRequest request) {
		return teamService.invite(request);
	}

	@PostMapping("/invites/{inviteId}/accept")
	public TeamInviteResponse acceptInvite(
		@PathVariable UUID inviteId,
		@Valid @RequestBody RespondTeamInviteRequest request
	) {
		return teamService.acceptInvite(inviteId, request);
	}

	@PostMapping("/invites/{inviteId}/decline")
	public TeamInviteResponse declineInvite(
		@PathVariable UUID inviteId,
		@Valid @RequestBody RespondTeamInviteRequest request
	) {
		return teamService.declineInvite(inviteId, request);
	}

	@DeleteMapping("/invites/{inviteId}/notification")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteNotification(@PathVariable UUID inviteId, @RequestParam String email) {
		teamService.deleteNotification(inviteId, email);
	}

	@PutMapping("/members/{userId}/sectors")
	public List<TeamMemberResponse> updateMemberSectors(
		@PathVariable UUID userId,
		@Valid @RequestBody UpdateMemberSectorsRequest request
	) {
		return teamService.updateMemberSectors(userId, request);
	}

	@DeleteMapping("/members/{userId}")
	public List<TeamMemberResponse> removeMemberFromCompany(@PathVariable UUID userId, @RequestParam String email) {
		return teamService.removeMemberFromCompany(userId, email);
	}

	@DeleteMapping("/sectors/{sectorId}/leave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leaveSector(@PathVariable UUID sectorId, @RequestParam String email) {
		teamService.leaveSector(sectorId, email);
	}
}

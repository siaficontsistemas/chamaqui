package com.helpdesk.helpdesk.api.profile;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.helpdesk.helpdesk.dto.common.OperationMessageResponse;
import com.helpdesk.helpdesk.dto.company.CompanyLogoResponse;
import com.helpdesk.helpdesk.dto.profile.ChangePasswordRequest;
import com.helpdesk.helpdesk.dto.profile.ProfileResponse;
import com.helpdesk.helpdesk.dto.profile.UpdateProfileRequest;
import com.helpdesk.helpdesk.service.AppSessionService;
import com.helpdesk.helpdesk.service.ProfileService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

	private final ProfileService profileService;
	private final AppSessionService appSessionService;

	public ProfileController(ProfileService profileService, AppSessionService appSessionService) {
		this.profileService = profileService;
		this.appSessionService = appSessionService;
	}

	@GetMapping
	public ProfileResponse getProfile(HttpSession session) {
		return profileService.getByEmail(appSessionService.requireCurrentEmail(session));
	}

	@PutMapping
	public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request, HttpSession session) {
		return profileService.update(
			new UpdateProfileRequest(
				appSessionService.requireCurrentEmail(session),
				request.fullName(),
				request.email(),
				request.phoneNumber(),
				request.companyName(),
				request.companyDocument()
			)
		);
	}

	@PutMapping("/password")
	public OperationMessageResponse changePassword(
		@Valid @RequestBody ChangePasswordRequest request,
		HttpSession session
	) {
		return profileService.changePassword(
			new ChangePasswordRequest(
				appSessionService.requireCurrentEmail(session),
				request.newPassword(),
				request.confirmPassword()
			)
		);
	}

	@PutMapping("/company/logo")
	public CompanyLogoResponse updateCompanyLogo(
		@RequestPart("file") MultipartFile file,
		HttpSession session
	) {
		return profileService.updateCompanyLogo(appSessionService.requireCurrentEmail(session), file);
	}

	@DeleteMapping("/company/logo")
	public CompanyLogoResponse deleteCompanyLogo(HttpSession session) {
		return profileService.deleteCompanyLogo(appSessionService.requireCurrentEmail(session));
	}

	@DeleteMapping
	public void deleteProfile(HttpSession session) {
		profileService.deleteByEmail(appSessionService.requireCurrentEmail(session));
	}

	@DeleteMapping("/company")
	public void deleteCompany(HttpSession session) {
		profileService.deleteCompanyByEmail(appSessionService.requireCurrentEmail(session));
	}
}

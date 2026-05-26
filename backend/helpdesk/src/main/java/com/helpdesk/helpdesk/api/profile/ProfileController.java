package com.helpdesk.helpdesk.api.profile;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.helpdesk.helpdesk.dto.company.CompanyLogoResponse;
import com.helpdesk.helpdesk.dto.common.OperationMessageResponse;
import com.helpdesk.helpdesk.dto.profile.ChangePasswordRequest;
import com.helpdesk.helpdesk.dto.profile.ProfileResponse;
import com.helpdesk.helpdesk.dto.profile.UpdateProfileRequest;
import com.helpdesk.helpdesk.service.ProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

	private final ProfileService profileService;

	public ProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@GetMapping
	public ProfileResponse getProfile(@RequestParam String email) {
		return profileService.getByEmail(email);
	}

	@PutMapping
	public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
		return profileService.update(request);
	}

	@PutMapping("/password")
	public OperationMessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
		return profileService.changePassword(request);
	}

	@PutMapping("/company/logo")
	public CompanyLogoResponse updateCompanyLogo(
		@RequestParam String email,
		@RequestPart("file") MultipartFile file
	) {
		return profileService.updateCompanyLogo(email, file);
	}

	@DeleteMapping("/company/logo")
	public CompanyLogoResponse deleteCompanyLogo(@RequestParam String email) {
		return profileService.deleteCompanyLogo(email);
	}

	@DeleteMapping
	public void deleteProfile(@RequestParam String email) {
		profileService.deleteByEmail(email);
	}

	@DeleteMapping("/company")
	public void deleteCompany(@RequestParam String email) {
		profileService.deleteCompanyByEmail(email);
	}
}

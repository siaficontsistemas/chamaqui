package com.helpdesk.helpdesk.dto.auth;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CompleteRegistrationRequest(@NotBlank String token,@NotBlank @Size(min=3,max=150) String fullName,@NotBlank @Email @Size(max=150) String email,@NotBlank @Size(min=11,max=20) String documentNumber,@NotBlank @Size(min=8,max=60) String password,@NotBlank String confirmPassword,@AssertTrue boolean acceptedTerms,@AssertTrue boolean acceptedPrivacyPolicy) {}

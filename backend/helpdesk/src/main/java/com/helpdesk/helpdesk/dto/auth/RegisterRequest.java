package com.helpdesk.helpdesk.dto.auth;

import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
	@NotBlank(message = "Informe o nome.")
	@Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres.")
	String fullName,
	@NotBlank(message = "Informe o email.")
	@Email(message = "Informe um email válido.")
	@Size(max = 150, message = "O email deve ter no máximo 150 caracteres.")
	String email,
	@Size(max = 30, message = "O telefone deve ter no máximo 30 caracteres.")
	String phoneNumber,
	@NotBlank(message = "Informe o CPF.")
	@Size(min = 11, max = 20, message = "O CPF deve ter entre 11 e 20 caracteres.")
	String documentNumber,
	UUID companyOwnerId,
	@Size(max = 150, message = "O nome da empresa deve ter no máximo 150 caracteres.")
	String companyName,
	@Size(max = 20, message = "O CNPJ da empresa deve ter no máximo 20 caracteres.")
	String companyDocument,
	@Pattern(regexp = "requester|responder|REQUESTER|RESPONDER", message = "O tipo de empresa informado é inválido.")
	String companyType,
	@NotBlank(message = "Informe a senha.")
	@Size(min = 8, max = 60, message = "A senha deve ter entre 8 e 60 caracteres.")
	String password,
	@NotBlank(message = "Informe o tipo de cadastro.")
	@Pattern(regexp = "admin|employee|user", message = "O perfil informado é inválido.")
	String role,
	@Size(max = 120, message = "O token do convite é inválido.")
	String inviteToken,
	@AssertTrue(message = "É necessário aceitar os termos para concluir o cadastro.")
	boolean acceptedTerms,
	@AssertTrue(message = "É necessário aceitar a política de privacidade para concluir o cadastro.")
	boolean acceptedPrivacyPolicy
) {
}

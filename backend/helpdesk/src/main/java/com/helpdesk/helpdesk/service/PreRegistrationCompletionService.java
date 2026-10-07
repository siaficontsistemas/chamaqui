package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.dto.auth.CompleteRegistrationRequest;
import com.helpdesk.helpdesk.dto.auth.CompleteRegistrationResponse;
import com.helpdesk.helpdesk.repository.CompanyMembershipRepository;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.UserRepository;
import com.helpdesk.helpdesk.util.BrazilianDocumentValidator;

@Service
public class PreRegistrationCompletionService {
 private final UserRepository userRepository; private final PasswordEncoder passwordEncoder; private final SensitiveTokenService sensitiveTokenService;
 private final CompanyMembershipRepository companyMembershipRepository; private final RoleRepository roleRepository;
 private final TenantAccessService tenantAccessService;
 public PreRegistrationCompletionService(UserRepository userRepository, PasswordEncoder passwordEncoder, SensitiveTokenService sensitiveTokenService, CompanyMembershipRepository companyMembershipRepository, RoleRepository roleRepository, TenantAccessService tenantAccessService) { this.userRepository=userRepository; this.passwordEncoder=passwordEncoder; this.sensitiveTokenService=sensitiveTokenService; this.companyMembershipRepository=companyMembershipRepository; this.roleRepository=roleRepository; this.tenantAccessService=tenantAccessService; }
 @Transactional public CompleteRegistrationResponse complete(CompleteRegistrationRequest request) {
  if (!request.password().equals(request.confirmPassword())) throw new IllegalArgumentException("As senhas precisam ser iguais.");
  User user=userRepository.findByRegistrationTokenHash(sensitiveTokenService.hashToken(request.token(), "O link de cadastro é inválido.")).orElseThrow(() -> new NotFoundException("O link de cadastro é inválido ou já expirou."));
  if (!user.isPreRegistered() || user.getRegistrationTokenExpiresAt()==null || user.getRegistrationTokenExpiresAt().isBefore(OffsetDateTime.now())) throw new IllegalArgumentException("O link de cadastro é inválido ou já expirou.");
  String expectedSubdomain = tenantAccessService.findPrimaryCompanyForUser(user).map(company -> company.getSubdomain()).orElse(null);
  String currentSubdomain = tenantAccessService.getCurrentTenant().map(tenant -> tenant.subdomain()).orElse(null);
  if (expectedSubdomain == null || expectedSubdomain.isBlank()) throw new IllegalArgumentException("A empresa do pré-cadastro não possui um subdomínio válido.");
  if (currentSubdomain == null || !expectedSubdomain.equalsIgnoreCase(currentSubdomain)) throw new IllegalArgumentException("Acesse o link pelo subdomínio correto da empresa.");
  String document=request.documentNumber().replaceAll("\\D", ""); if (!BrazilianDocumentValidator.isValidCpf(document)) throw new IllegalArgumentException("Informe um CPF válido.");
  String email=request.email().trim().toLowerCase(Locale.ROOT); if (userRepository.existsByEmailIgnoreCase(email)) throw new IllegalArgumentException("Já existe um cadastro associado a esse email."); if (userRepository.existsByDocumentNumber(document)) throw new IllegalArgumentException("Já existe um cadastro associado a esse CPF.");
  User companyOwner = user.getCompanyOwner();
  if (companyOwner == null) throw new IllegalArgumentException("O pré-cadastro não possui uma empresa vinculada.");
  Role employeeRole = roleRepository.findByCode("EMPLOYEE")
   .orElseThrow(() -> new NotFoundException("Perfil de funcionário não encontrado."));
  if (!companyMembershipRepository.existsByUserIdAndCompanyOwnerId(user.getId(), companyOwner.getId())) {
   com.helpdesk.helpdesk.domain.CompanyMembership membership = new com.helpdesk.helpdesk.domain.CompanyMembership();
   membership.setUser(user);
   membership.setCompanyOwner(companyOwner);
   companyMembershipRepository.save(membership);
  }
  user.getRoles().add(employeeRole);
  user.setFullName(request.fullName().trim()); user.setEmail(email); user.setDocumentNumber(document); user.setPasswordHash(passwordEncoder.encode(request.password())); user.setEmailVerified(true); user.setPreRegistered(false); user.setRegistrationTokenHash(null); user.setRegistrationTokenExpiresAt(null); user.setTermsAcceptedAt(OffsetDateTime.now()); user.setPrivacyPolicyAcceptedAt(OffsetDateTime.now()); userRepository.save(user);
  return new CompleteRegistrationResponse("Cadastro finalizado com sucesso. Agora você já pode acessar o Chamaqui.");
 }
}

package com.helpdesk.helpdesk.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.Company;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.repository.CompanyMembershipRepository;
import com.helpdesk.helpdesk.repository.CompanyRepository;
import com.helpdesk.helpdesk.repository.UserRepository;
import com.helpdesk.helpdesk.tenant.ResolvedTenant;
import com.helpdesk.helpdesk.tenant.TenantContext;

@Service
public class TenantAccessService {

	private final UserRepository userRepository;
	private final CompanyMembershipRepository companyMembershipRepository;
	private final CompanyRepository companyRepository;
	private final FrontendPublicUrlService frontendPublicUrlService;

	public TenantAccessService(
		UserRepository userRepository,
		CompanyMembershipRepository companyMembershipRepository,
		CompanyRepository companyRepository,
		FrontendPublicUrlService frontendPublicUrlService
	) {
		this.userRepository = userRepository;
		this.companyMembershipRepository = companyMembershipRepository;
		this.companyRepository = companyRepository;
		this.frontendPublicUrlService = frontendPublicUrlService;
	}

	public Optional<ResolvedTenant> getCurrentTenant() {
		return Optional.ofNullable(TenantContext.get());
	}

	public boolean hasCurrentTenant() {
		return TenantContext.hasTenant();
	}

	public Optional<UUID> getCurrentTenantOwnerUserId() {
		return getCurrentTenant()
			.map(ResolvedTenant::ownerUserId)
			.filter(ownerUserId -> ownerUserId != null);
	}

	public UUID requireCurrentTenantOwnerUserId() {
		return getCurrentTenantOwnerUserId()
			.orElseThrow(() -> new IllegalArgumentException("Nenhum tenant ativo foi identificado para esta requisição."));
	}

	@Transactional(readOnly = true)
	public User loadCurrentTenantOwner() {
		return userRepository.findById(requireCurrentTenantOwnerUserId())
			.orElseThrow(() -> new NotFoundException("Empresa do tenant atual não encontrada."));
	}

	public void ensureCompanyMatchesCurrentTenant(UUID companyOwnerId, String message) {
		if (companyOwnerId == null || !hasCurrentTenant()) {
			return;
		}

		if (!companyOwnerId.equals(requireCurrentTenantOwnerUserId())) {
			throw new IllegalArgumentException(message);
		}
	}

	public void ensureUserBelongsToCurrentTenant(User user, String message) {
		if (user == null || !hasCurrentTenant()) {
			return;
		}

		if (!belongsToCurrentTenant(user)) {
			throw new IllegalArgumentException(message);
		}
	}

	public boolean belongsToCurrentTenant(User user) {
		if (user == null) {
			return false;
		}
		if (!hasCurrentTenant()) {
			return true;
		}

		UUID tenantOwnerUserId = requireCurrentTenantOwnerUserId();
		return belongsToTenantOwner(user, tenantOwnerUserId)
			|| companyMembershipRepository.existsByUserIdAndCompanyOwnerId(user.getId(), tenantOwnerUserId);
	}

	@Transactional(readOnly = true)
	public Optional<Company> findPrimaryCompanyForUser(User user) {
		if (user == null || user.getId() == null) {
			return Optional.empty();
		}

		Optional<Company> ownedCompany = findPrimaryCompanyByOwnerChain(user);
		if (ownedCompany.isPresent()) {
			return ownedCompany;
		}

		return companyMembershipRepository.findByUserIdOrderByJoinedAtAsc(user.getId()).stream()
			.map(membership -> membership.getCompanyOwner())
			.filter(companyOwner -> companyOwner != null)
			.map(this::findPrimaryCompanyByOwnerChain)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.findFirst();
	}

	private boolean belongsToTenantOwner(User user, UUID tenantOwnerUserId) {
		User currentUser = user;
		int depth = 0;

		while (currentUser != null && currentUser.getId() != null && depth < 10) {
			if (tenantOwnerUserId.equals(currentUser.getId())) {
				return true;
			}
			currentUser = currentUser.getCompanyOwner();
			depth += 1;
		}

		return false;
	}

	private Optional<Company> findPrimaryCompanyByOwnerChain(User user) {
		User currentUser = user;
		int depth = 0;

		while (currentUser != null && currentUser.getId() != null && depth < 10) {
			Optional<Company> company = companyRepository.findByOwnerUserId(currentUser.getId());
			if (company.isPresent()) {
				return company;
			}
			currentUser = currentUser.getCompanyOwner();
			depth += 1;
		}

		return Optional.empty();
	}

	@Transactional(readOnly = true)
	public void ensureMainHostLoginAllowed(User user) {
		if (hasCurrentTenant()) {
			return;
		}

		findPrimaryCompanyForUser(user).ifPresent(company -> {
			throw new IllegalArgumentException(
				"Este usuario deve acessar pelo subdominio da empresa: "
					+ frontendPublicUrlService.buildAccessUrl(company.getSubdomain())
			);
		});
	}
}

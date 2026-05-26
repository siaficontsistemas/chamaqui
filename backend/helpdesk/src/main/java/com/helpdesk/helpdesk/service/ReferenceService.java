package com.helpdesk.helpdesk.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.domain.CompanyPartnershipStatus;
import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.reference.ReferenceItemResponse;
import com.helpdesk.helpdesk.repository.CompanyPartnershipRepository;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.SectorRepository;
import com.helpdesk.helpdesk.repository.TicketPriorityRepository;
import com.helpdesk.helpdesk.repository.TicketStatusRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class ReferenceService {

	private final RoleRepository roleRepository;
	private final TicketStatusRepository ticketStatusRepository;
	private final TicketPriorityRepository ticketPriorityRepository;
	private final UserRepository userRepository;
	private final SectorRepository sectorRepository;
	private final CompanyPartnershipRepository companyPartnershipRepository;
	private final TenantAccessService tenantAccessService;

	public ReferenceService(
		RoleRepository roleRepository,
		TicketStatusRepository ticketStatusRepository,
		TicketPriorityRepository ticketPriorityRepository,
		UserRepository userRepository,
		SectorRepository sectorRepository,
		CompanyPartnershipRepository companyPartnershipRepository,
		TenantAccessService tenantAccessService
	) {
		this.roleRepository = roleRepository;
		this.ticketStatusRepository = ticketStatusRepository;
		this.ticketPriorityRepository = ticketPriorityRepository;
		this.userRepository = userRepository;
		this.sectorRepository = sectorRepository;
		this.companyPartnershipRepository = companyPartnershipRepository;
		this.tenantAccessService = tenantAccessService;
	}

	@Transactional(readOnly = true)
	public List<ReferenceItemResponse> getRoles() {
		return roleRepository.findAll().stream()
			.sorted(java.util.Comparator.comparing(role -> role.getName().toLowerCase()))
			.map(role -> new ReferenceItemResponse(role.getId(), role.getCode(), role.getName(), null))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<ReferenceItemResponse> getTicketStatuses() {
		return ticketStatusRepository.findAllByOrderBySortOrderAsc().stream()
			.map(status -> new ReferenceItemResponse(status.getId(), status.getCode(), status.getName(), status.getSortOrder()))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<ReferenceItemResponse> getTicketPriorities() {
		return ticketPriorityRepository.findAllByOrderBySortOrderAsc().stream()
			.map(priority -> new ReferenceItemResponse(priority.getId(), priority.getCode(), priority.getName(), priority.getSortOrder()))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<ReferenceItemResponse> getCompanies(String companyTypeValue) {
		CompanyType companyType = CompanyType.fromValue(companyTypeValue);
		if (companyType == null) {
			throw new IllegalArgumentException("Informe o tipo de empresa para listar as empresas disponíveis.");
		}

		if (tenantAccessService.hasCurrentTenant()) {
			User tenantCompany = tenantAccessService.loadCurrentTenantOwner();
			if (tenantCompany.getCompanyType() != companyType) {
				if (tenantCompany.getCompanyType() == CompanyType.RESPONDER && companyType == CompanyType.REQUESTER) {
					return companyPartnershipRepository.findVisibleByCompanyId(tenantCompany.getId()).stream()
						.filter(partnership -> partnership.getStatus() == CompanyPartnershipStatus.ACCEPTED)
						.map(partnership -> partnership.getRequesterCompany().getId().equals(tenantCompany.getId())
							? partnership.getTargetCompany()
							: partnership.getRequesterCompany())
						.filter(partnerCompany -> partnerCompany.getCompanyType() == CompanyType.REQUESTER)
						.map(partnerCompany -> new ReferenceItemResponse(
							partnerCompany.getId(),
							partnerCompany.getCompanyDocument(),
							partnerCompany.getCompanyName(),
							null
						))
						.distinct()
						.toList();
				}

				return List.of();
			}

			return List.of(new ReferenceItemResponse(
				tenantCompany.getId(),
				tenantCompany.getCompanyDocument(),
				tenantCompany.getCompanyName(),
				null
			));
		}

		return userRepository.findVisibleCompaniesByType(companyType).stream()
			.map(user -> new ReferenceItemResponse(user.getId(), user.getCompanyDocument(), user.getCompanyName(), null))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<ReferenceItemResponse> getCompanySectors(java.util.UUID companyOwnerId) {
		java.util.UUID effectiveCompanyOwnerId = tenantAccessService.getCurrentTenantOwnerUserId()
			.orElse(companyOwnerId);
		tenantAccessService.ensureCompanyMatchesCurrentTenant(
			effectiveCompanyOwnerId,
			"Os setores disponíveis devem pertencer ao tenant atual."
		);

		return sectorRepository.findActiveByCreatedByIdOrderByNameAsc(effectiveCompanyOwnerId).stream()
			.map(sector -> new ReferenceItemResponse(sector.getId(), sector.getSlug(), sector.getName(), null))
			.toList();
	}
}

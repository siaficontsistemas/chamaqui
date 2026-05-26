package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.CompanyPartnership;
import com.helpdesk.helpdesk.domain.CompanyPartnershipNotification;
import com.helpdesk.helpdesk.domain.CompanyPartnershipNotificationType;
import com.helpdesk.helpdesk.domain.CompanyPartnershipStatus;
import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.Sector;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.company.CompanyPartnershipResponse;
import com.helpdesk.helpdesk.dto.company.CompanySearchResultResponse;
import com.helpdesk.helpdesk.dto.company.CreateCompanyPartnershipRequest;
import com.helpdesk.helpdesk.dto.company.RespondCompanyPartnershipRequest;
import com.helpdesk.helpdesk.dto.sector.SectorResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketTargetSectorResponse;
import com.helpdesk.helpdesk.repository.CompanyPartnershipRepository;
import com.helpdesk.helpdesk.repository.CompanyPartnershipNotificationRepository;
import com.helpdesk.helpdesk.repository.SectorRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class CompanyPartnershipService {

	private final CompanyPartnershipRepository companyPartnershipRepository;
	private final CompanyPartnershipNotificationRepository companyPartnershipNotificationRepository;
	private final UserRepository userRepository;
	private final SectorRepository sectorRepository;
	private final TicketService ticketService;
	private final TenantAccessService tenantAccessService;
	private final ScopedUserLookupService scopedUserLookupService;

	public CompanyPartnershipService(
		CompanyPartnershipRepository companyPartnershipRepository,
		CompanyPartnershipNotificationRepository companyPartnershipNotificationRepository,
		UserRepository userRepository,
		SectorRepository sectorRepository,
		TicketService ticketService,
		TenantAccessService tenantAccessService,
		ScopedUserLookupService scopedUserLookupService
	) {
		this.companyPartnershipRepository = companyPartnershipRepository;
		this.companyPartnershipNotificationRepository = companyPartnershipNotificationRepository;
		this.userRepository = userRepository;
		this.sectorRepository = sectorRepository;
		this.ticketService = ticketService;
		this.tenantAccessService = tenantAccessService;
		this.scopedUserLookupService = scopedUserLookupService;
	}

	@Transactional(readOnly = true)
	public List<CompanySearchResultResponse> searchCompanies(String email, String query) {
		User admin = loadAdminCompanyByEmail(email);
		String normalizedQuery = normalizeSearchQuery(query);
		String normalizedDocumentQuery = normalizedQuery.replaceAll("\\D", "");
		CompanyType compatibleCompanyType = resolveCompatibleCompanyType(admin.getCompanyType());

		return userRepository.searchAdminCompanies(
			admin.getId(),
			compatibleCompanyType,
			normalizedQuery,
			normalizedDocumentQuery
		).stream()
			.map(company -> new CompanySearchResultResponse(
				company.getId(),
				company.getCompanyName(),
				company.getCompanyDocument(),
				company.getCompanyType() == null ? null : company.getCompanyType().name()
			))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<CompanyPartnershipResponse> listMine(String email) {
		User admin = loadAdminCompanyByEmail(email);

		return companyPartnershipRepository.findVisibleByCompanyId(admin.getId()).stream()
			.map(partnership -> toResponse(partnership, admin.getId()))
			.toList();
	}

	@Transactional
	public CompanyPartnershipResponse create(CreateCompanyPartnershipRequest request) {
		User requesterAdmin = loadAdminCompanyByEmail(request.requesterEmail());
		User targetCompany = userRepository.findById(request.targetCompanyId())
			.orElseThrow(() -> new NotFoundException("Empresa de destino não encontrada."));

		ensureAdminCompany(targetCompany, "A empresa selecionada não está disponível para parceria.");

		if (requesterAdmin.getId().equals(targetCompany.getId())) {
			throw new IllegalArgumentException("Você não pode solicitar parceria para a própria empresa.");
		}
		ensureCompatibleCompanyTypes(requesterAdmin, targetCompany);

		List<CompanyPartnership> activeOrPendingPartnerships = companyPartnershipRepository.findByCompanyPairAndStatuses(
			requesterAdmin.getId(),
			targetCompany.getId(),
			EnumSet.of(CompanyPartnershipStatus.PENDING, CompanyPartnershipStatus.ACCEPTED)
		);

		if (!activeOrPendingPartnerships.isEmpty()) {
			CompanyPartnership existingPartnership = activeOrPendingPartnerships.get(0);
			if (existingPartnership.getStatus() == CompanyPartnershipStatus.ACCEPTED) {
				throw new IllegalArgumentException("Essas empresas já possuem uma parceria ativa.");
			}
			throw new IllegalArgumentException("Já existe uma solicitação pendente entre essas empresas.");
		}

		CompanyPartnership partnership = new CompanyPartnership();
		partnership.setRequesterCompany(requesterAdmin);
		partnership.setTargetCompany(targetCompany);
		partnership.setRequestedBy(requesterAdmin);
		partnership.setCreatedAt(OffsetDateTime.now());
		partnership.setStatus(CompanyPartnershipStatus.PENDING);
		CompanyPartnership savedPartnership = companyPartnershipRepository.save(partnership);
		createNotification(savedPartnership, targetCompany, requesterAdmin, CompanyPartnershipNotificationType.REQUESTED);

		return toResponse(savedPartnership, requesterAdmin.getId());
	}

	@Transactional
	public CompanyPartnershipResponse accept(UUID partnershipId, RespondCompanyPartnershipRequest request) {
		User admin = loadAdminCompanyByEmail(request.email());
		CompanyPartnership partnership = loadPendingPartnershipForResponse(partnershipId, admin);
		ensureCompatibleCompanyTypes(partnership.getRequesterCompany(), partnership.getTargetCompany());

		partnership.setStatus(CompanyPartnershipStatus.ACCEPTED);
		partnership.setRespondedBy(admin);
		partnership.setRespondedAt(OffsetDateTime.now());
		CompanyPartnership savedPartnership = companyPartnershipRepository.save(partnership);
		hidePendingRequestNotifications(savedPartnership);
		createNotification(
			savedPartnership,
			savedPartnership.getRequesterCompany(),
			admin,
			CompanyPartnershipNotificationType.ACCEPTED
		);

		return toResponse(savedPartnership, admin.getId());
	}

	@Transactional
	public CompanyPartnershipResponse decline(UUID partnershipId, RespondCompanyPartnershipRequest request) {
		User admin = loadAdminCompanyByEmail(request.email());
		CompanyPartnership partnership = loadPendingPartnershipForResponse(partnershipId, admin);

		partnership.setStatus(CompanyPartnershipStatus.DECLINED);
		partnership.setRespondedBy(admin);
		partnership.setRespondedAt(OffsetDateTime.now());
		CompanyPartnership savedPartnership = companyPartnershipRepository.save(partnership);
		hidePendingRequestNotifications(savedPartnership);

		return toResponse(savedPartnership, admin.getId());
	}

	@Transactional
	public void unlink(UUID partnershipId, String email) {
		User admin = loadAdminCompanyByEmail(email);
		CompanyPartnership partnership = companyPartnershipRepository.findById(partnershipId)
			.orElseThrow(() -> new NotFoundException("Parceria não encontrada."));

		boolean isCompanyParticipant = partnership.getRequesterCompany().getId().equals(admin.getId())
			|| partnership.getTargetCompany().getId().equals(admin.getId());
		if (!isCompanyParticipant) {
			throw new IllegalArgumentException("Somente empresas vinculadas podem desfazer esta parceria.");
		}

		if (partnership.getStatus() != CompanyPartnershipStatus.ACCEPTED) {
			throw new IllegalArgumentException("Somente parcerias ativas podem ser desfeitas.");
		}

		User otherCompany = partnership.getRequesterCompany().getId().equals(admin.getId())
			? partnership.getTargetCompany()
			: partnership.getRequesterCompany();
		createNotification(partnership, otherCompany, admin, CompanyPartnershipNotificationType.UNLINKED);
		companyPartnershipRepository.delete(partnership);
	}

	@Transactional(readOnly = true)
	public List<TicketTargetSectorResponse> listTicketTargets(String email) {
		User user = loadUserByEmail(email);
		tenantAccessService.ensureUserBelongsToCurrentTenant(user, "Esse usuário não pertence ao tenant atual.");

		if (tenantAccessService.hasCurrentTenant()) {
			return sectorRepository.findActiveByCreatedByIdOrderByNameAsc(
				tenantAccessService.requireCurrentTenantOwnerUserId()
			).stream()
				.map(this::toTicketTargetSectorResponse)
				.toList();
		}

		User currentCompany = resolveOperatingCompany(user);
		List<CompanyPartnership> partnerships = companyPartnershipRepository.findVisibleByCompanyId(currentCompany.getId());

		Set<UUID> partnerCompanyIds = partnerships.stream()
			.filter(partnership -> partnership.getStatus() == CompanyPartnershipStatus.ACCEPTED)
			.filter(partnership -> isCompatibleCompanyPair(
				partnership.getRequesterCompany(),
				partnership.getTargetCompany()
			))
			.map(partnership -> partnership.getRequesterCompany().getId().equals(currentCompany.getId())
				? partnership.getTargetCompany().getId()
				: partnership.getRequesterCompany().getId())
			.collect(Collectors.toCollection(java.util.LinkedHashSet::new));

		if (partnerCompanyIds.isEmpty()) {
			return List.of();
		}

		return sectorRepository.findActiveByCreatedByIdInOrderByNameAsc(partnerCompanyIds).stream()
			.map(this::toTicketTargetSectorResponse)
			.toList();
	}

	private CompanyPartnership loadPendingPartnershipForResponse(UUID partnershipId, User admin) {
		CompanyPartnership partnership = companyPartnershipRepository.findById(partnershipId)
			.orElseThrow(() -> new NotFoundException("Solicitação de parceria não encontrada."));

		if (!partnership.getTargetCompany().getId().equals(admin.getId())) {
			throw new IllegalArgumentException("Somente o administrador da empresa destinatária pode responder a solicitação.");
		}

		if (partnership.getStatus() != CompanyPartnershipStatus.PENDING) {
			throw new IllegalArgumentException("Essa solicitação de parceria já foi respondida.");
		}

		return partnership;
	}

	private CompanyPartnershipResponse toResponse(CompanyPartnership partnership, UUID currentCompanyId) {
		return new CompanyPartnershipResponse(
			partnership.getId(),
			partnership.getStatus().name(),
			partnership.getRequesterCompany().getId(),
			partnership.getRequesterCompany().getCompanyName(),
			partnership.getRequesterCompany().getCompanyDocument(),
			partnership.getTargetCompany().getId(),
			partnership.getTargetCompany().getCompanyName(),
			partnership.getTargetCompany().getCompanyDocument(),
			partnership.getCreatedAt(),
			partnership.getRespondedAt(),
			partnership.getStatus() == CompanyPartnershipStatus.PENDING
				&& partnership.getTargetCompany().getId().equals(currentCompanyId),
			partnership.getRequesterCompany().getId().equals(currentCompanyId)
		);
	}

	private SectorResponse toSectorResponse(Sector sector) {
		String companyName = sector.getCreatedBy().getCompanyName();
		if (companyName == null || companyName.isBlank()) {
			companyName = sector.getCreatedBy().getFullName();
		}

		return new SectorResponse(
			sector.getId(),
			sector.getName(),
			sector.getSlug(),
			sector.getDescription(),
			sector.isActive(),
			sector.getCreatedBy().getId(),
			companyName,
			sector.getCreatedBy().getCompanyDocument(),
			sector.getCreatedBy().getEmail(),
			sector.getMembers().size()
		);
	}

	private TicketTargetSectorResponse toTicketTargetSectorResponse(Sector sector) {
		SectorResponse sectorResponse = toSectorResponse(sector);
		return new TicketTargetSectorResponse(
			sectorResponse.id(),
			sectorResponse.name(),
			sectorResponse.slug(),
			sectorResponse.description(),
			sectorResponse.active(),
			sectorResponse.companyOwnerId(),
			sectorResponse.companyName(),
			sectorResponse.companyDocument(),
			sectorResponse.createdByEmail(),
			sectorResponse.memberCount(),
			ticketService.listAvailableAssigneesForSector(sector.getId(), sector.getCreatedBy().getId())
		);
	}

	private void createNotification(
		CompanyPartnership partnership,
		User recipient,
		User actor,
		CompanyPartnershipNotificationType type
	) {
		CompanyPartnershipNotification notification = new CompanyPartnershipNotification();
		notification.setCompanyPartnershipId(partnership.getId());
		notification.setRecipient(recipient);
		notification.setActorUser(actor);
		notification.setRequesterCompanyId(partnership.getRequesterCompany().getId());
		notification.setRequesterCompanyName(resolveCompanyName(partnership.getRequesterCompany()));
		notification.setTargetCompanyId(partnership.getTargetCompany().getId());
		notification.setTargetCompanyName(resolveCompanyName(partnership.getTargetCompany()));
		notification.setType(type);
		companyPartnershipNotificationRepository.save(notification);
	}

	private void hidePendingRequestNotifications(CompanyPartnership partnership) {
		List<CompanyPartnershipNotification> notifications = companyPartnershipNotificationRepository
			.findByCompanyPartnershipIdAndRecipientIdAndTypeAndHiddenFalse(
				partnership.getId(),
				partnership.getTargetCompany().getId(),
				CompanyPartnershipNotificationType.REQUESTED
			);

		for (CompanyPartnershipNotification notification : notifications) {
			notification.setHidden(true);
		}

		if (!notifications.isEmpty()) {
			companyPartnershipNotificationRepository.saveAll(notifications);
		}
	}

	private User loadAdminCompanyByEmail(String email) {
		User user = loadUserByEmail(email);
		ensureAdminCompany(user, "Somente administradores com empresa cadastrada podem gerenciar parcerias.");
		return user;
	}

	private User loadUserByEmail(String email) {
		return scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário responsável não encontrado."));
	}

	private User resolveOperatingCompany(User user) {
		if (hasRole(user, "ADMIN") && user.getCompanyName() != null && user.getCompanyDocument() != null) {
			return user;
		}

		if (user.getCompanyOwner() != null) {
			return user.getCompanyOwner();
		}

		throw new IllegalArgumentException("Seu usuário não está vinculado a uma empresa autorizada para abrir chamados.");
	}

	private void ensureAdminCompany(User user, String message) {
		if (!hasRole(user, "ADMIN") || user.getCompanyName() == null || user.getCompanyDocument() == null) {
			throw new IllegalArgumentException(message);
		}
	}

	private void ensureCompatibleCompanyTypes(User requesterCompany, User targetCompany) {
		if (!isCompatibleCompanyPair(requesterCompany, targetCompany)) {
			throw new IllegalArgumentException(
				"A parceria so pode ser feita entre uma empresa que pergunta e uma empresa que responde chamados."
			);
		}
	}

	private boolean isCompatibleCompanyPair(User firstCompany, User secondCompany) {
		if (firstCompany == null || secondCompany == null) {
			return false;
		}

		CompanyType firstType = firstCompany.getCompanyType();
		CompanyType secondType = secondCompany.getCompanyType();

		return (firstType == CompanyType.REQUESTER && secondType == CompanyType.RESPONDER)
			|| (firstType == CompanyType.RESPONDER && secondType == CompanyType.REQUESTER);
	}

	private CompanyType resolveCompatibleCompanyType(CompanyType companyType) {
		if (companyType == CompanyType.REQUESTER) {
			return CompanyType.RESPONDER;
		}
		if (companyType == CompanyType.RESPONDER) {
			return CompanyType.REQUESTER;
		}

		throw new IllegalArgumentException("O tipo da empresa administrada nao permite criar parcerias.");
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getRoles().stream().anyMatch(role -> roleCode.equalsIgnoreCase(role.getCode()));
	}

	private String resolveCompanyName(User user) {
		if (user.getCompanyName() != null && !user.getCompanyName().isBlank()) {
			return user.getCompanyName();
		}
		return user.getFullName();
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeSearchQuery(String query) {
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("Informe o nome ou o CNPJ da empresa para pesquisar.");
		}

		String normalizedQuery = query.trim();
		if (normalizedQuery.length() < 2) {
			throw new IllegalArgumentException("Digite pelo menos 2 caracteres para pesquisar a empresa.");
		}

		return normalizedQuery;
	}
}

package com.helpdesk.helpdesk.service;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.Company;
import com.helpdesk.helpdesk.repository.CompanyRepository;
import com.helpdesk.helpdesk.tenant.ResolvedTenant;
import com.helpdesk.helpdesk.tenant.TenantContext;

@Service
public class TenantExecutionService {

	private final CompanyRepository companyRepository;
	private final TransactionTemplate transactionTemplate;

	public TenantExecutionService(CompanyRepository companyRepository, TransactionTemplate transactionTemplate) {
		this.companyRepository = companyRepository;
		this.transactionTemplate = transactionTemplate;
	}

	public void runInTenantByOwnerUserId(UUID ownerUserId, Runnable action) {
		executeInTenantByOwnerUserId(ownerUserId, () -> {
			action.run();
			return null;
		});
	}

	public <T> T executeInTenantByOwnerUserId(UUID ownerUserId, Supplier<T> action) {
		ResolvedTenant tenant = companyRepository.findByOwnerUserId(ownerUserId)
			.map(this::toResolvedTenant)
			.orElseThrow(() -> new NotFoundException("Empresa do tenant não encontrada."));

		ResolvedTenant previousTenant = TenantContext.get();
		try {
			TenantContext.set(tenant);
			return transactionTemplate.execute(status -> action.get());
		} finally {
			if (previousTenant == null) {
				TenantContext.clear();
			} else {
				TenantContext.set(previousTenant);
			}
		}
	}

	private ResolvedTenant toResolvedTenant(Company company) {
		return new ResolvedTenant(
			company.getId(),
			company.getOwnerUser() == null ? null : company.getOwnerUser().getId(),
			company.getCompanyName(),
			company.getCompanyType() == null ? null : company.getCompanyType().name(),
			company.getSubdomain(),
			company.getSchemaName(),
			company.getLogoUrl(),
			company.getLoginLogoUrl()
		);
	}
}

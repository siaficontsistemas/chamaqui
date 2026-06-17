package com.helpdesk.helpdesk.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.helpdesk.helpdesk.domain.PlatformAdminUser;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.tenant.ResolvedTenant;

@Service
public class AuditTrailService {

	private static final Logger logger = LoggerFactory.getLogger("AUDIT");

	private final TenantAccessService tenantAccessService;

	public AuditTrailService(TenantAccessService tenantAccessService) {
		this.tenantAccessService = tenantAccessService;
	}

	public void recordUserAction(String action, User actor, String targetType, UUID targetId) {
		record(
			action,
			"app-user",
			actor == null || actor.getId() == null ? "-" : actor.getId().toString(),
			actor == null ? "-" : safe(actor.getEmail()),
			targetType,
			targetId == null ? "-" : targetId.toString()
		);
	}

	public void recordPlatformAdminAction(String action, PlatformAdminUser actor, String targetType, UUID targetId) {
		record(
			action,
			"platform-admin",
			actor == null || actor.getId() == null ? "-" : actor.getId().toString(),
			actor == null ? "-" : safe(actor.getEmail()),
			targetType,
			targetId == null ? "-" : targetId.toString()
		);
	}

	public void recordAnonymousAction(String action, String actorEmail, String targetType, UUID targetId) {
		record(
			action,
			"anonymous",
			"-",
			safe(actorEmail),
			targetType,
			targetId == null ? "-" : targetId.toString()
		);
	}

	private void record(
		String action,
		String actorType,
		String actorId,
		String actorEmail,
		String targetType,
		String targetId
	) {
		ResolvedTenant tenant = tenantAccessService.getCurrentTenant().orElse(null);
		logger.info(
			"action={} actorType={} actorId={} actorEmail={} tenantOwnerId={} tenantSchema={} targetType={} targetId={}",
			safe(action),
			safe(actorType),
			safe(actorId),
			safe(actorEmail),
			tenant == null || tenant.ownerUserId() == null ? "-" : tenant.ownerUserId(),
			tenant == null ? "public" : safe(tenant.schemaName()),
			safe(targetType),
			safe(targetId)
		);
	}

	private String safe(String value) {
		if (value == null || value.isBlank()) {
			return "-";
		}
		return value.trim().replaceAll("[\\r\\n\\t]+", " ");
	}
}

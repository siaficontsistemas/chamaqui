package com.helpdesk.helpdesk.tenant;

public final class TenantContext {

	private static final String DEFAULT_SCHEMA = "public";
	private static final ThreadLocal<ResolvedTenant> CURRENT_TENANT = new ThreadLocal<>();

	private TenantContext() {
	}

	public static void set(ResolvedTenant tenant) {
		if (tenant == null) {
			CURRENT_TENANT.remove();
			return;
		}
		CURRENT_TENANT.set(tenant);
	}

	public static ResolvedTenant get() {
		return CURRENT_TENANT.get();
	}

	public static String getCurrentSchemaName() {
		ResolvedTenant tenant = CURRENT_TENANT.get();
		if (tenant == null || tenant.schemaName() == null || tenant.schemaName().isBlank()) {
			return DEFAULT_SCHEMA;
		}
		return tenant.schemaName();
	}

	public static boolean hasTenant() {
		return CURRENT_TENANT.get() != null;
	}

	public static String getDefaultSchema() {
		return DEFAULT_SCHEMA;
	}

	public static void clear() {
		CURRENT_TENANT.remove();
	}
}

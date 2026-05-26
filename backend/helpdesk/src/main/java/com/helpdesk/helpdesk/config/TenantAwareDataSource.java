package com.helpdesk.helpdesk.config;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.AbstractDataSource;

import com.helpdesk.helpdesk.tenant.TenantContext;

public class TenantAwareDataSource extends AbstractDataSource {

	private final DataSource delegate;

	public TenantAwareDataSource(DataSource delegate) {
		this.delegate = delegate;
	}

	@Override
	public Connection getConnection() throws SQLException {
		return prepareConnection(delegate.getConnection());
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return prepareConnection(delegate.getConnection(username, password));
	}

	private Connection prepareConnection(Connection connection) throws SQLException {
		applySearchPath(connection);
		return wrapConnection(connection);
	}

	private void applySearchPath(Connection connection) throws SQLException {
		String schemaName = TenantContext.getCurrentSchemaName();
		String searchPath = TenantContext.getDefaultSchema().equals(schemaName)
			? "public"
			: quoteIdentifier(schemaName) + ", public";

		try (Statement statement = connection.createStatement()) {
			statement.execute("set search_path to " + searchPath);
		}
	}

	private Connection wrapConnection(Connection connection) {
		InvocationHandler handler = (proxy, method, args) -> {
			if ("close".equals(method.getName())) {
				resetSearchPath(connection);
				return method.invoke(connection, args);
			}

			return method.invoke(connection, args);
		};

		return (Connection) Proxy.newProxyInstance(
			Connection.class.getClassLoader(),
			new Class<?>[] { Connection.class },
			handler
		);
	}

	private void resetSearchPath(Connection connection) throws SQLException {
		if (connection.isClosed()) {
			return;
		}

		try (Statement statement = connection.createStatement()) {
			statement.execute("set search_path to public");
		}
	}

	private String quoteIdentifier(String identifier) {
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}
}

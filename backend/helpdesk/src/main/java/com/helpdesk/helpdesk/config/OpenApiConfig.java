package com.helpdesk.helpdesk.config;

import java.lang.reflect.InvocationTargetException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	Object helpdeskOpenApi() {
		try {
			Object openApi = newInstance("io.swagger.v3.oas.models.OpenAPI");
			Object info = newInstance("io.swagger.v3.oas.models.info.Info");
			Object contact = newInstance("io.swagger.v3.oas.models.info.Contact");
			Object license = newInstance("io.swagger.v3.oas.models.info.License");

			invoke(contact, "name", "Helpdesk");
			invoke(contact, "email", "contato@helpdesk.local");
			invoke(license, "name", "Uso interno");
			invoke(info, "title", "Helpdesk API");
			invoke(info, "version", "v1");
			invoke(info, "description", "API inicial do Helpdesk alinhada ao banco PostgreSQL, aos módulos do frontend e à evolução futura do sistema.");
			invoke(info, "contact", contact);
			invoke(info, "license", license);
			return invoke(openApi, "info", info);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Não foi possível configurar o OpenAPI.", exception);
		}
	}

	@Bean
	Object authApi() {
		return groupedOpenApi("auth", "/api/v1/auth/**", "/api/v1/profile/**");
	}

	@Bean
	Object teamApi() {
		return groupedOpenApi("team", "/api/v1/sectors/**", "/api/v1/team/**");
	}

	@Bean
	Object ticketApi() {
		return groupedOpenApi("tickets", "/api/v1/tickets/**", "/api/v1/reports/**", "/api/v1/reference/**");
	}

	private Object groupedOpenApi(String group, String... paths) {
		try {
			Class<?> groupedOpenApiClass = Class.forName("org.springdoc.core.models.GroupedOpenApi");
			Object builder = groupedOpenApiClass.getMethod("builder").invoke(null);
			invoke(builder, "group", group);
			builder.getClass().getMethod("pathsToMatch", String[].class).invoke(builder, (Object) paths);
			return builder.getClass().getMethod("build").invoke(builder);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Não foi possível configurar o agrupamento OpenAPI " + group + ".", exception);
		}
	}

	private Object newInstance(String className) throws ReflectiveOperationException {
		return Class.forName(className).getDeclaredConstructor().newInstance();
	}

	private Object invoke(Object target, String methodName, Object argument) throws ReflectiveOperationException {
		try {
			return target.getClass().getMethod(methodName, argument.getClass()).invoke(target, argument);
		} catch (InvocationTargetException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof ReflectiveOperationException reflectiveOperationException) {
				throw reflectiveOperationException;
			}
			throw exception;
		}
	}
}

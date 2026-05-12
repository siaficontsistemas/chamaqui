package com.helpdesk.helpdesk.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class CnpjLookupService {

	private final RestClient restClient;

	public CnpjLookupService(
		@Value("${app.validation.cnpj-api-base-url:https://brasilapi.com.br/api}") String baseUrl,
		@Value("${app.validation.cnpj-api-connect-timeout-ms:5000}") int connectTimeoutMs,
		@Value("${app.validation.cnpj-api-read-timeout-ms:8000}") int readTimeoutMs
	) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Math.max(connectTimeoutMs, 1000));
		requestFactory.setReadTimeout(Math.max(readTimeoutMs, 1000));

		this.restClient = RestClient.builder()
			.requestFactory(requestFactory)
			.baseUrl(normalizeBaseUrl(baseUrl))
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.build();
	}

	public void ensureCompanyExists(String cnpj) {
		try {
			BrasilApiCnpjResponse response = restClient.get()
				.uri("/cnpj/v1/{cnpj}", cnpj)
				.retrieve()
				.body(BrasilApiCnpjResponse.class);

			if (response == null || response.cnpj() == null || !cnpj.equals(response.cnpj().replaceAll("\\D", ""))) {
				throw new IllegalArgumentException("Informe um CNPJ existente para a empresa.");
			}
		} catch (RestClientResponseException exception) {
			if (exception.getStatusCode().is4xxClientError()) {
				throw new IllegalArgumentException("Informe um CNPJ existente para a empresa.");
			}
			throw new IllegalArgumentException("Não foi possível validar o CNPJ da empresa agora. Tente novamente em instantes.");
		} catch (ResourceAccessException exception) {
			throw new IllegalArgumentException("Não foi possível validar o CNPJ da empresa agora. Tente novamente em instantes.");
		}
	}

	private String normalizeBaseUrl(String baseUrl) {
		String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
		if (normalizedBaseUrl.isBlank()) {
			throw new IllegalArgumentException("Defina a URL base da consulta pública de CNPJ.");
		}
		return normalizedBaseUrl.endsWith("/")
			? normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1)
			: normalizedBaseUrl;
	}

	private record BrasilApiCnpjResponse(
		String cnpj,
		String razao_social,
		String descricao_situacao_cadastral
	) {
	}
}

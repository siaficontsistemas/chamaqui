package com.helpdesk.helpdesk.service;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class SlugService {

	public String slugify(String value) {
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
			.replaceAll("\\p{M}+", "")
			.toLowerCase(Locale.ROOT)
			.trim()
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("(^-|-$)", "");

		return normalized.isBlank() ? "setor" : normalized;
	}
}

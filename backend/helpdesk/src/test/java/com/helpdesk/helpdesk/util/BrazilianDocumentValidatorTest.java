package com.helpdesk.helpdesk.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BrazilianDocumentValidatorTest {

	@Test
	void shouldAcceptValidCpf() {
		assertTrue(BrazilianDocumentValidator.isValidCpf("52998224725"));
	}

	@Test
	void shouldRejectCpfWithInvalidCheckDigits() {
		assertFalse(BrazilianDocumentValidator.isValidCpf("52998224724"));
	}

	@Test
	void shouldRejectCpfWithRepeatedDigits() {
		assertFalse(BrazilianDocumentValidator.isValidCpf("11111111111"));
	}

	@Test
	void shouldRejectCpfWithNonNumericCharacters() {
		assertFalse(BrazilianDocumentValidator.isValidCpf("5299822472A"));
	}
}

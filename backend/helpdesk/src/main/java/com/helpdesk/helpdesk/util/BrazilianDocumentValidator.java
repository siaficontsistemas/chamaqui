package com.helpdesk.helpdesk.util;

public final class BrazilianDocumentValidator {

	private BrazilianDocumentValidator() {
	}

	public static boolean isValidCpf(String cpf) {
		if (cpf == null || cpf.length() != 11 || hasAllDigitsEqual(cpf)) {
			return false;
		}

		for (int index = 0; index < cpf.length(); index++) {
			if (!Character.isDigit(cpf.charAt(index))) {
				return false;
			}
		}

		return calculateCpfCheckDigit(cpf, 9) == Character.getNumericValue(cpf.charAt(9))
			&& calculateCpfCheckDigit(cpf, 10) == Character.getNumericValue(cpf.charAt(10));
	}

	private static int calculateCpfCheckDigit(String cpf, int length) {
		int sum = 0;
		int weight = length + 1;

		for (int index = 0; index < length; index++) {
			sum += Character.getNumericValue(cpf.charAt(index)) * (weight - index);
		}

		int remainder = (sum * 10) % 11;
		return remainder == 10 ? 0 : remainder;
	}

	private static boolean hasAllDigitsEqual(String value) {
		char firstDigit = value.charAt(0);
		for (int index = 1; index < value.length(); index++) {
			if (value.charAt(index) != firstDigit) {
				return false;
			}
		}
		return true;
	}
}

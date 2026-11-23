package com.helpdesk.helpdesk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BusinessHoursServiceTest {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
	private static final String WEEKDAYS = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";
	private static final String PERIODS = "08:00-12:00,14:00-18:00";

	// 2026-11-23 é segunda-feira; 2026-11-27 sexta; 2026-11-28 sábado; 2026-11-29 domingo.
	@ParameterizedTest(name = "{0} -> dentro do expediente: {1}")
	@CsvSource({
		"2026-11-23T07:59, false",
		"2026-11-23T08:00, true",
		"2026-11-23T11:59, true",
		"2026-11-23T12:00, false",
		"2026-11-23T13:59, false",
		"2026-11-23T14:00, true",
		"2026-11-23T17:59, true",
		"2026-11-23T18:00, false",
		"2026-11-27T17:59, true",
		"2026-11-27T18:00, false",
		"2026-11-28T10:00, false",
		"2026-11-28T15:00, false",
		"2026-11-29T10:00, false",
		"2026-11-29T15:00, false"
	})
	void shouldRespectPeriodBoundariesAndWeekends(String localDateTime, boolean expected) {
		BusinessHoursService service = serviceAt(localDateTime);

		assertEquals(expected, service.isWithinBusinessHours());
	}

	@Test
	void shouldEvaluateInSaoPauloTimeZoneRegardlessOfServerZone() {
		// 13:30 UTC = 10:30 em São Paulo (UTC-3): dentro do expediente.
		Clock utcClock = Clock.fixed(LocalDateTime.parse("2026-11-23T13:30").toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
		BusinessHoursService service = new BusinessHoursService(true, "America/Sao_Paulo", WEEKDAYS, PERIODS, utcClock);

		assertTrue(service.isWithinBusinessHours());
	}

	@Test
	void shouldTreatDisabledBusinessHoursAsAlwaysOpen() {
		Clock sundayNight = Clock.fixed(LocalDateTime.parse("2026-11-29T23:00").atZone(SAO_PAULO).toInstant(), SAO_PAULO);
		BusinessHoursService service = new BusinessHoursService(false, "America/Sao_Paulo", WEEKDAYS, PERIODS, sundayNight);

		assertTrue(service.isWithinBusinessHours());
	}

	@Test
	void shouldDescribeConfiguredHoursInMessage() {
		BusinessHoursService service = serviceAt("2026-11-23T19:00");

		assertFalse(service.isWithinBusinessHours());
		assertEquals(
			"de segunda a sexta, das 08:00 às 12:00 e das 14:00 às 18:00",
			service.describeBusinessHours()
		);
		assertTrue(service.buildOutsideBusinessHoursMessage().contains("fora do horário de atendimento"));
		assertTrue(service.buildOutsideBusinessHoursMessage().contains("horário de Brasília"));
	}

	@Test
	void shouldDescribeCustomDays() {
		Clock clock = Clock.fixed(LocalDateTime.parse("2026-11-28T09:00").atZone(SAO_PAULO).toInstant(), SAO_PAULO);
		BusinessHoursService service = new BusinessHoursService(true, "America/Sao_Paulo", "SATURDAY, MONDAY", "08:00-12:00", clock);

		assertTrue(service.isWithinBusinessHours());
		assertEquals("segunda e sábado, das 08:00 às 12:00", service.describeBusinessHours());
	}

	@Test
	void shouldRejectInvalidConfiguration() {
		Clock clock = Clock.systemUTC();
		assertThrows(IllegalStateException.class, () -> new BusinessHoursService(true, "America/Sao_Paulo", "FUNDAY", PERIODS, clock));
		assertThrows(IllegalStateException.class, () -> new BusinessHoursService(true, "America/Sao_Paulo", WEEKDAYS, "18:00-08:00", clock));
		assertThrows(IllegalStateException.class, () -> new BusinessHoursService(true, "America/Sao_Paulo", WEEKDAYS, "8h-12h", clock));
		assertThrows(IllegalStateException.class, () -> new BusinessHoursService(true, "Lua/Base", WEEKDAYS, PERIODS, clock));
	}

	private BusinessHoursService serviceAt(String localDateTime) {
		Clock clock = Clock.fixed(LocalDateTime.parse(localDateTime).atZone(SAO_PAULO).toInstant(), SAO_PAULO);
		return new BusinessHoursService(true, "America/Sao_Paulo", WEEKDAYS, PERIODS, clock);
	}
}

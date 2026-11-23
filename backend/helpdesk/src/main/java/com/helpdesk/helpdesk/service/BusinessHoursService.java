package com.helpdesk.helpdesk.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Horário de expediente do atendimento, configurado por propriedades {@code app.business-hours.*}.
 *
 * <p>Cada período é semiaberto: {@code 08:00-12:00} inclui 08:00 e exclui 12:00,
 * de modo que 11:59 está dentro do expediente e 12:00 já está fora.</p>
 */
@Service
public class BusinessHoursService {

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final EnumSet<DayOfWeek> WEEKDAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

	private final boolean enabled;
	private final ZoneId zoneId;
	private final Set<DayOfWeek> workingDays;
	private final List<Period> periods;
	private final Clock clock;

	@Autowired
	public BusinessHoursService(
		@Value("${app.business-hours.enabled:true}") boolean enabled,
		@Value("${app.business-hours.zone:America/Sao_Paulo}") String zone,
		@Value("${app.business-hours.days:MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY}") String days,
		@Value("${app.business-hours.periods:08:00-12:00,14:00-18:00}") String periods
	) {
		this(enabled, zone, days, periods, Clock.systemUTC());
	}

	BusinessHoursService(boolean enabled, String zone, String days, String periods, Clock clock) {
		this.enabled = enabled;
		this.zoneId = parseZone(zone);
		this.workingDays = parseDays(days);
		this.periods = parsePeriods(periods);
		this.clock = clock;
	}

	/**
	 * Indica se o momento atual está dentro do expediente.
	 * Com {@code app.business-hours.enabled=false} o atendimento é considerado sempre aberto.
	 */
	public boolean isWithinBusinessHours() {
		if (!enabled) {
			return true;
		}
		ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId);
		if (!workingDays.contains(now.getDayOfWeek())) {
			return false;
		}
		LocalTime time = now.toLocalTime();
		return periods.stream().anyMatch(period -> period.contains(time));
	}

	/** Texto do horário, por exemplo "de segunda a sexta, das 08:00 às 12:00 e das 14:00 às 18:00". */
	public String describeBusinessHours() {
		String periodText = periods.stream()
			.map(period -> "das %s às %s".formatted(TIME_FORMAT.format(period.start()), TIME_FORMAT.format(period.end())))
			.collect(Collectors.collectingAndThen(Collectors.toList(), BusinessHoursService::joinWithAnd));
		return describeDays() + ", " + periodText;
	}

	/** Mensagem enviada ao cliente que tenta abrir atendimento fora do expediente. */
	public String buildOutsideBusinessHoursMessage() {
		return """
			Olá! No momento estamos fora do horário de atendimento.
			Nosso horário é %s (%s).
			Não é possível abrir chamados agora. Envie sua mensagem novamente dentro do horário de atendimento.
			""".formatted(describeBusinessHours(), describeZone()).trim();
	}

	private String describeZone() {
		return "America/Sao_Paulo".equals(zoneId.getId()) ? "horário de Brasília" : "fuso " + zoneId.getId();
	}

	private String describeDays() {
		if (workingDays.equals(WEEKDAYS)) {
			return "de segunda a sexta";
		}
		List<String> names = workingDays.stream()
			.sorted(Comparator.naturalOrder())
			.map(BusinessHoursService::dayName)
			.toList();
		return joinWithAnd(names);
	}

	private static String dayName(DayOfWeek day) {
		return switch (day) {
			case MONDAY -> "segunda";
			case TUESDAY -> "terça";
			case WEDNESDAY -> "quarta";
			case THURSDAY -> "quinta";
			case FRIDAY -> "sexta";
			case SATURDAY -> "sábado";
			case SUNDAY -> "domingo";
		};
	}

	private static String joinWithAnd(List<String> values) {
		if (values.size() <= 1) {
			return String.join("", values);
		}
		return String.join(", ", values.subList(0, values.size() - 1)) + " e " + values.get(values.size() - 1);
	}

	private static ZoneId parseZone(String zone) {
		try {
			return ZoneId.of(zone == null || zone.isBlank() ? "America/Sao_Paulo" : zone.trim());
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Fuso inválido em app.business-hours.zone: " + zone, exception);
		}
	}

	private static Set<DayOfWeek> parseDays(String days) {
		EnumSet<DayOfWeek> parsedDays = EnumSet.noneOf(DayOfWeek.class);
		for (String day : splitCsv(days)) {
			try {
				parsedDays.add(DayOfWeek.valueOf(day.toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException exception) {
				throw new IllegalStateException("Dia inválido em app.business-hours.days: " + day, exception);
			}
		}
		if (parsedDays.isEmpty()) {
			throw new IllegalStateException("Informe ao menos um dia em app.business-hours.days.");
		}
		return parsedDays;
	}

	private static List<Period> parsePeriods(String periods) {
		List<Period> parsedPeriods = new ArrayList<>();
		for (String period : splitCsv(periods)) {
			String[] bounds = period.split("-");
			if (bounds.length != 2) {
				throw new IllegalStateException("Período inválido em app.business-hours.periods: " + period);
			}
			try {
				LocalTime start = LocalTime.parse(bounds[0].trim());
				LocalTime end = LocalTime.parse(bounds[1].trim());
				if (!end.isAfter(start)) {
					throw new IllegalStateException("O fim deve ser depois do início em app.business-hours.periods: " + period);
				}
				parsedPeriods.add(new Period(start, end));
			} catch (DateTimeParseException exception) {
				throw new IllegalStateException("Horário inválido em app.business-hours.periods: " + period, exception);
			}
		}
		if (parsedPeriods.isEmpty()) {
			throw new IllegalStateException("Informe ao menos um período em app.business-hours.periods.");
		}
		parsedPeriods.sort(Comparator.comparing(Period::start));
		return List.copyOf(parsedPeriods);
	}

	private static List<String> splitCsv(String value) {
		if (value == null) {
			return List.of();
		}
		return Arrays.stream(value.split(","))
			.map(String::trim)
			.filter(item -> !item.isEmpty())
			.toList();
	}

	private record Period(LocalTime start, LocalTime end) {
		boolean contains(LocalTime time) {
			return !time.isBefore(start) && time.isBefore(end);
		}
	}
}

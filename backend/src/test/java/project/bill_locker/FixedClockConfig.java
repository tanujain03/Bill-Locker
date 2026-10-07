package project.bill_locker;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** "Today" is always 7 Oct 2026 in tests, so "30 days left" and "this month" never drift. */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfig {

	public static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

	@Bean
	@Primary // wins over the real system clock
	Clock fixedClock() {
		return Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC);
	}
}

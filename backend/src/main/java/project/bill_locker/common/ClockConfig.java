package project.bill_locker.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock that decides what "today" is. Services ask it instead of calling
 * LocalDate.now() directly, so tests can pin a date (FixedClockConfig).
 */
@Configuration
class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}
}

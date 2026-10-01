package project.bill_locker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // runs @Scheduled methods, e.g. the background document reader
public class BillLockerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BillLockerApplication.class, args);
	}

}

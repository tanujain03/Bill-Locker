package project.bill_locker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the app: Spring finds every class in this package (and below) and wires them together. */
@SpringBootApplication
public class BillLockerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BillLockerApplication.class, args);
	}
}

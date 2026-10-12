package project.bill_locker.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import project.bill_locker.document.DocumentReadWorker;
import project.bill_locker.gmail.GmailImportWorker;
import project.bill_locker.gmail.GmailProperties;
import project.bill_locker.gmail.GmailScanWorker;

/** All background timers in one place; off in tests (app.workers.enabled=false). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.workers.enabled", havingValue = "true", matchIfMissing = true)
public class WorkerSchedule {

	private final DocumentReadWorker reader;
	private final GmailScanWorker gmailScanner;
	private final GmailImportWorker gmailImporter;
	private final GmailProperties gmailProperties;

	public WorkerSchedule(DocumentReadWorker reader, GmailScanWorker gmailScanner, GmailImportWorker gmailImporter,
			GmailProperties gmailProperties) {
		this.reader = reader;
		this.gmailScanner = gmailScanner;
		this.gmailImporter = gmailImporter;
		this.gmailProperties = gmailProperties;
	}

	// fixedDelay: the next run starts 3 s after the last one ends, so runs never overlap.
	@Scheduled(fixedDelay = 3000, initialDelay = 10000)
	void readDocuments() {
		reader.runOnce();
	}

	@Scheduled(fixedDelay = 3000, initialDelay = 10000)
	void scanGmail() {
		if (gmailProperties.isConfigured()) { // without setup there is nothing to scan and no key for the tokens
			gmailScanner.runOnce();
		}
	}

	@Scheduled(fixedDelay = 3000, initialDelay = 10000)
	void importGmail() {
		if (gmailProperties.isConfigured()) { // no setup, no key to decrypt the tokens
			gmailImporter.runOnce();
		}
	}
}

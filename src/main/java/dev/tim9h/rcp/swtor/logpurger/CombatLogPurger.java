package dev.tim9h.rcp.swtor.logpurger;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.swing.filechooser.FileSystemView;

import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;

import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.swtor.SwtorViewFactory;

public class CombatLogPurger extends SimpleFileVisitor<Path> {

	@InjectLogger
	private Logger logger;

	@Inject
	private Settings settings;

	@Inject
	private EventManager eventManager;

	private long maxAge = -1;

	private int deletedLogsCount = 0;

	public void initCombatLogPurgerScheduler() {
		var deletionTime = settings.getString(SwtorViewFactory.SETTINGR_COMBATLOGS_DELETIONTIME);
		ZonedDateTime nextRun;
		try {
			var time = LocalTime.parse(deletionTime, DateTimeFormatter.ofPattern("HH:mm"));
			nextRun = ZonedDateTime.of(LocalDate.now(), time, ZoneId.systemDefault());
		} catch (DateTimeParseException e) {
			logger.warn(() -> "Unable to parse combat log deletion time. Using 00:00 instead of " + deletionTime);
			nextRun = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0);
		}
		var now = ZonedDateTime.now();
		if (now.compareTo(nextRun) > 0) {
			nextRun = nextRun.plusDays(1);
		}
		var duration = Duration.between(now, nextRun);
		long initialDelay = duration.getSeconds();

		try (var scheduler = Executors.newScheduledThreadPool(1)) {
			scheduler.scheduleAtFixedRate(this::deleteCombatLogs, initialDelay, TimeUnit.DAYS.toSeconds(1),
					TimeUnit.SECONDS);
		}
	}

	public void deleteCombatLogs() {
		logger.info(() -> "Started combat file deletion");
		var combatLogManagmentEnabled = settings.getInt(SwtorViewFactory.SETTINGR_COMBATLOGS_MAXAGE) != null;
		if (combatLogManagmentEnabled) {
			var path = Path.of(FileSystemView.getFileSystemView().getDefaultDirectory().toString(),
					"Star Wars - The Old Republic", "CombatLogs");
			if (Files.exists(path)) {
				try {
					Files.walkFileTree(path, this);
				} catch (IOException e) {
					logger.warn(() -> "Unable to visit combat log" + e);
				}
			} else {
				logger.warn(() -> "Not managing CombatLogs. Path doesn't exist: " + path);
			}
		}
	}

	@Override
	public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
		var ageInDays = Duration.between(attrs.creationTime().toInstant(), Instant.now());
		if (ageInDays.toDays() > getMaxAge()) {
			logger.info(() -> "Deleting combat log " + path);
			try {
				Files.delete(path);
				deletedLogsCount++;
			} catch (IOException | SecurityException e) {
				logger.error(() -> "Error while deleting combat log", e);
			}
		}
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
		if (deletedLogsCount > 0) {
			var msg = String.format("Deleted %d combat logs", Integer.valueOf(deletedLogsCount));
			logger.info(() -> msg);
			eventManager.echoAsync(msg);
			eventManager.showToast("SWToR", msg);
		} else {
			logger.info(() -> "No combat logs older than " + maxAge + " days found");
		}
		maxAge = -1;
		deletedLogsCount = 0;
		return super.postVisitDirectory(dir, exc);
	}

	private long getMaxAge() {
		if (maxAge == -1) {
			maxAge = settings.getLong(SwtorViewFactory.SETTINGR_COMBATLOGS_MAXAGE).longValue();
		}
		return maxAge;
	}

}

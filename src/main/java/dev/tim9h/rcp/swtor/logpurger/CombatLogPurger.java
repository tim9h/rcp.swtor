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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.filechooser.FileSystemView;

import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.swtor.SwtorView;

@Singleton
public class CombatLogPurger extends SimpleFileVisitor<Path> {

	@InjectLogger
	private Logger logger;

	@Inject
	private Settings settings;

	@Inject
	private EventManager eventManager;

	private long maxAge = -1;

	private int deletedLogsCount = 0;

	private ScheduledExecutorService scheduler;

	private ScheduledFuture<?> combatLogPurge;

	public void initCombatLogPurgerScheduler() {
		var deletionTime = settings.getString(SwtorView.SETTINGR_COMBATLOGS_DELETIONTIME);
		ZonedDateTime nextRun;
		try {
			var time = LocalTime.parse(deletionTime, DateTimeFormatter.ofPattern("HH:mm"));
			nextRun = ZonedDateTime.of(LocalDate.now(ZoneId.systemDefault()), time, ZoneId.systemDefault());
		} catch (DateTimeParseException _) {
			logger.warn(() -> "Unable to parse combat log deletion time. Using 00:00 instead of " + deletionTime);
			nextRun = ZonedDateTime.now(ZoneId.systemDefault()).withHour(0).withMinute(0).withSecond(0);
		}

		var now = ZonedDateTime.now(ZoneId.systemDefault());
		if (now.compareTo(nextRun) > 0) {
			nextRun = nextRun.plusDays(1);
		}
		var duration = Duration.between(now, nextRun);
		var initialDelay = duration.getSeconds();

		if (scheduler == null) {
			scheduler = Executors.newScheduledThreadPool(1, runnable -> {
				var thread = new Thread(runnable, "SWToRCombatLogPurger");
				thread.setDaemon(true);
				return thread;
			});
		}

		if (combatLogPurge != null) {
			combatLogPurge.cancel(false);
		}
		combatLogPurge = scheduler.scheduleAtFixedRate(this::deleteCombatLogs, initialDelay, TimeUnit.DAYS.toSeconds(1),
				TimeUnit.SECONDS);

		var nr = nextRun;
		logger.debug(() -> String.format("Scheduled with initial delay %d (%s)", Long.valueOf(initialDelay), nr));
	}

	public synchronized void deleteCombatLogs() {
		logger.info(() -> "Started combat file deletion");
		var maxAgeSetting = settings.getLong(SwtorView.SETTINGR_COMBATLOGS_MAXAGE);
		if (maxAgeSetting == null) {
			eventManager.echo("Combat log management is disabled");
			return;
		}
		maxAge = maxAgeSetting.longValue();
		deletedLogsCount = 0;
		var path = Path.of(FileSystemView.getFileSystemView().getDefaultDirectory().toString(),
				"Star Wars - The Old Republic", "CombatLogs");
		if (Files.exists(path)) {
			try {
				Files.walkFileTree(path, this);
			} catch (IOException e) {
				logger.warn(() -> "Unable to visit combat log" + e);
			}
			reportResult();
		} else {
			logger.warn(() -> "Not managing CombatLogs. Path doesn't exist: " + path);
			eventManager.echo("Combat log directory not found");
		}
	}

	private void reportResult() {
		if (deletedLogsCount > 0) {
			var msg = String.format("Deleted %d combat logs", Integer.valueOf(deletedLogsCount));
			logger.info(() -> msg);
			eventManager.echo(msg);
			eventManager.showToast(settings.getAppTitle(), msg);
		} else {
			var msg = String.format("No combat logs older than %d days found", Long.valueOf(maxAge));
			eventManager.echo(msg);
			logger.info(() -> msg);
		}
	}

	@Override
	public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
		var ageInDays = Duration.between(attrs.creationTime().toInstant(), Instant.now());
		if (ageInDays.toDays() > maxAge) {
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

	public void close() {
		logger.debug(() -> "Shutting down combatlog purge task and scheduler");
		if (combatLogPurge != null) {
			combatLogPurge.cancel(false);
		}
		if (scheduler != null) {
			scheduler.shutdownNow();
		}
		logger.debug(() -> "Combatlog purger shutdown complete");
	}

}

package dev.tim9h.rcp.swtor;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.swing.filechooser.FileSystemView;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;

import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.spi.CommandBuilder;
import dev.tim9h.rcp.spi.CommandNode;
import dev.tim9h.rcp.spi.Mode;
import dev.tim9h.rcp.spi.Plugin;
import dev.tim9h.rcp.swtor.loadingscreen.LoadingScreenWatcher;
import dev.tim9h.rcp.swtor.logpurger.CombatLogPurger;
import dev.tim9h.swtor.parser.CombatLogWatcher;
import dev.tim9h.swtor.parser.bean.CombatLog;

public class SwtorView implements Plugin {

	public static final String SETTINGR_INSTALLATION_LOCATION = "swtor.installation.location";

	public static final String SETTINGR_LOADINGSCREEN_LOCATION = "swtor.loadingscreen.location";

	public static final String SETTINGR_COMBATLOGS_MAXAGE = "swtor.combatlogs.maxage";

	public static final String SETTINGR_COMBATLOGS_DELETIONTIME = "swtor.combatlogs.deletiontime";

	private static final String COMBATLOGS = "combatlogs";

	private static final String PURGE = "purge";

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	@Inject
	private CombatLogPurger combatLogPurger;

	@Inject
	private LoadingScreenWatcher loadingScreenWatcher;

	@Inject
	private CombatLogWatcher combatLogWatcher;

	@Override
	public String getName() {
		return "SWToR Tools";
	}

	@Override
	public String getId() {
		return "swtor";
	}

	@Override
	public void init() {
		combatLogPurger.initCombatLogPurgerScheduler();
	}

	@Override
	public Optional<List<Mode>> getModes() {
		return Optional.of(Arrays.asList(new Mode() {

			@Override
			public void onEnable() {
				loadingScreenWatcher.startWatching();
				eventManager.echo("Managing SWToR Loadingscreen");
			}

			@Override
			public void onDisable() {
				loadingScreenWatcher.stopWatching();
				eventManager.echo("Stopped SWToR Loadingscreen management");
			}

			@Override
			public String getName() {
				return "loadingsreen";
			}
		}, new Mode() {

			@Override
			public void onEnable() {
				combatLogWatcher.startWatching(log -> acceptLog(log));
				eventManager.echo("Parsing SWToR Combatlogs");

			}

			@Override
			public void onDisable() {
				combatLogWatcher.stopWatching();
				eventManager.echo("Stopped parsing");
			}

			@Override
			public String getName() {
				return "parser";
			}
		}));
	}

	@Override
	public void onShutdown() {
		combatLogPurger.close();
		loadingScreenWatcher.stopWatching();
		combatLogWatcher.stopWatching();
	}

	@Override
	public Optional<CommandNode> getCommands() {
		return new CommandBuilder().command("swtor") //
				.child("screenshotdir", _ -> CompletableFuture.runAsync(this::openScreenshotDirectory)).up()
				.child(COMBATLOGS, _ -> CompletableFuture.runAsync(this::openCombatLogsDirectory)).child(PURGE, _ -> {
					eventManager.showWaitingIndicator();
					CompletableFuture.runAsync(() -> combatLogPurger.deleteCombatLogs());
				}).build();
	}

	private void acceptLog(CombatLog log) {
		if ("AreaEntered".equals(log.getEffect().getEvent()) && StringUtils.isNotBlank(log.getEffect().getArea())) {
			logger.info(() -> log.getSource().getName() + " entered" + log.getEffect().getArea());
			eventManager.showToast(log.getEffect().getEffect(), log.getEffect().getArea());
		}
	}

	private void openScreenshotDirectory() {
		try {
			var directory = Path.of(FileSystemView.getFileSystemView().getDefaultDirectory().toString(),
					"Star Wars - The Old Republic", "Screenshots");
			Desktop.getDesktop().open(directory.toFile());
		} catch (InvalidPathException | IOException e) {
			logger.warn(() -> "Unable to screenshots directory: " + e.getMessage(), e);
		}
	}

	private void openCombatLogsDirectory() {
		try {
			var directory = Path.of(FileSystemView.getFileSystemView().getDefaultDirectory().toString(),
					"Star Wars - The Old Republic", "CombatLogs");
			Desktop.getDesktop().open(directory.toFile());
		} catch (InvalidPathException | IOException e) {
			logger.warn(() -> "Unable to combatlogs directory: " + e.getMessage(), e);
		}
	}

	@Override
	public Map<String, String> getSettingsContributions() {
		return Map.of(SETTINGR_INSTALLATION_LOCATION, "C:\\games\\SWToR", SETTINGR_LOADINGSCREEN_LOCATION, "",
				SETTINGR_COMBATLOGS_MAXAGE, "5", SETTINGR_COMBATLOGS_DELETIONTIME, "23:50");
	}

}

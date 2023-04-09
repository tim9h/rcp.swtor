package dev.tim9h.rcp.swtor.loadingscreen;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Injector;

import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.swtor.SwtorViewFactory;
import javafx.application.Platform;

public class LoadingScreenWatcher {

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	@Inject
	private Settings settings;

	private AtomicBoolean running = new AtomicBoolean(false);

	private static final List<WatchService> watchServices = new ArrayList<>();

	private Thread thread;

	@Inject
	public LoadingScreenWatcher(Injector injector) {
		injector.injectMembers(this);
	}

	public void startWatching() {
		logger.debug(() -> "Start watching and replace loadingscreen");
		replaceLoadingscreen(false);

		var path = getLoadingscreenPath();
		if (path == null || Files.notExists(path)) {
			logger.warn(() -> "Unable to start loadingscreen watcher: loading screen not found: " + path);
			running.set(false);
			return;
		}
		startWatcherThread(path);
	}

	private void startWatcherThread(Path path) {
		running.set(true);
		logger.debug(() -> "Starting loadingscreen watcher thread");
		thread = new Thread(() -> {
			try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
				path.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
				watchServices.add(watchService);
				while (running.get()) {
					running.set(pollEvents(watchService, path));
				}
			} catch (IOException | InterruptedException e) {
				Thread.currentThread().interrupt();
				logger.error(() -> "Error while watching loadingscreen", e);
			} catch (ClosedWatchServiceException e) {
				// do nothing
			}
		}, "SWToRLoadingscreenWatcher");
		thread.setDaemon(true);
		thread.start();
	}

	private Path getLoadingscreenPath() {
		var location = settings.getString(SwtorViewFactory.SETTINGR_INSTALLATION_LOCATION);
		if (location != null) {
			return Paths.get(location + "\\swtor\\retailclient\\LoadingScreens\\LoadingScreen.jpg");
		}
		logger.warn(() -> "SWToR installation location not specified");
		return null;
	}

	private Path getCustomscreenPath() {
		var location = settings.getString(SwtorViewFactory.SETTINGR_LOADINGSCREEN_LOCATION);
		if (location != null) {
			return Paths.get(location);
		}
		logger.warn(() -> "Custom loadingscreen location not specified");
		return null;
	}

	public void stopWatching() {
		running.set(false);
		logger.debug(() -> "Shutting down Loadingscreen watcher");
		watchServices.forEach(service -> {
			try {
				service.close();
			} catch (IOException e) {
				logger.error(() -> "Unable to close watchservice");
			}
		});
		thread.interrupt();
	}

	private boolean pollEvents(WatchService watchService, Path path)
			throws InterruptedException, ClosedWatchServiceException {
		var key = watchService.take();
		Thread.sleep(1000); // eliminate multiple events (content + change time)
		for (var event : key.pollEvents()) {
			if (path.endsWith(((Path) event.context()).toString())) {
				stopWatching();
				onFileChanged();
				Platform.runLater(() -> startWatcherThread(getLoadingscreenPath()));
				break;
			}
		}
		return key.reset();
	}

	private void onFileChanged() {
		logger.debug(() -> "Loadingscreen changed");
		replaceLoadingscreen(true);
	}

	private void replaceLoadingscreen(boolean withToast) {
		var source = getCustomscreenPath();
		var destination = getLoadingscreenPath();
		if (source != null && Files.exists(source) && destination != null && Files.exists(destination)) {
			try {
				FileUtils.copyFile(source.toFile(), destination.toFile());
				if (withToast) {
					logger.debug(() -> "Showing loadingscreen toast");
					eventManager.showToastAsync("SWToR", "Loadingscreen restored");
				}
				logger.info(() -> "Loading screen restored");
			} catch (IOException e) {
				logger.error(() -> "Unable to restore loadingscreen");
			}
		} else {
			logger.warn(() -> "Unable to restore loading screen: Either source (" + source + ") or destination ("
					+ destination + ") does not exist");
		}
	}

}

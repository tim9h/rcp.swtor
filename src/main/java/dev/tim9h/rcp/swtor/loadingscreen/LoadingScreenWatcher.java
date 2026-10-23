package dev.tim9h.rcp.swtor.loadingscreen;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Injector;

import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.swtor.SwtorView;

public class LoadingScreenWatcher {

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	@Inject
	private Settings settings;

	private AtomicBoolean running = new AtomicBoolean(false);

	private volatile WatchService watchService;

	private volatile Thread thread;

	@Inject
	public LoadingScreenWatcher(Injector injector) {
		injector.injectMembers(this);
	}

	public synchronized void startWatching() {
		stopWatching();
		logger.debug(() -> "Start watching and replace loadingscreen");

		var path = getLoadingscreenPath();
		if (path == null || Files.notExists(path)) {
			logger.warn(() -> "Unable to start loadingscreen watcher: loading screen not found: " + path);
			return;
		}
		startWatcherThread(path);
	}

	private void startWatcherThread(Path path) {
		running.set(true);
		logger.debug(() -> "Starting loadingscreen watcher thread");
		var watcher = new Thread(() -> watch(path), "SWToRLoadingscreenWatcher");
		watcher.setDaemon(true);
		thread = watcher;
		watcher.start();
	}

	private void watch(Path path) {
		try (var service = FileSystems.getDefault().newWatchService()) {
			watchService = service;
			path.getParent().register(service, StandardWatchEventKinds.ENTRY_MODIFY);
			replaceLoadingscreen(false);
			drain(service);
			while (running.get()) {
				if (pollEvents(service, path)) {
					onFileChanged();
					drain(service);
				}
			}
		} catch (InterruptedException _) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			logger.error(() -> "Error while watching loadingscreen", e);
		} catch (ClosedWatchServiceException _) {
			// stopped
		} finally {
			watchService = null;
		}
	}

	/**
	 * Discards events caused by our own file replacement.
	 */
	private static void drain(WatchService service) throws InterruptedException {
		Thread.sleep(1000);
		var key = service.poll();
		if (key != null) {
			key.pollEvents();
			key.reset();
		}
	}

	private Path getLoadingscreenPath() {
		var location = settings.getString(SwtorView.SETTINGR_INSTALLATION_LOCATION);
		if (location != null) {
			return Paths.get(location + "\\swtor\\retailclient\\LoadingScreens\\LoadingScreen.jpg");
		}
		logger.warn(() -> "SWToR installation location not specified");
		return null;
	}

	private Path getCustomscreenPath() {
		var location = settings.getString(SwtorView.SETTINGR_LOADINGSCREEN_LOCATION);
		if (location != null) {
			return Paths.get(location);
		}
		logger.warn(() -> "Custom loadingscreen location not specified");
		return null;
	}

	public synchronized void stopWatching() {
		running.set(false);
		var service = watchService;
		if (service != null) {
			logger.debug(() -> "Shutting down Loadingscreen watcher");
			try {
				service.close();
			} catch (IOException _) {
				logger.error(() -> "Unable to close watchservice");
			}
		}
		var current = thread;
		if (current != null && current != Thread.currentThread()) {
			current.interrupt();
		}
		thread = null;
	}

	/**
	 * @return true if the loadingscreen file was modified
	 */
	private boolean pollEvents(WatchService service, Path path) throws InterruptedException {
		var key = service.take();
		Thread.sleep(1000); // eliminate multiple events (content + change time)
		var changed = false;
		for (var event : key.pollEvents()) {
			if (path.endsWith(((Path) event.context()).toString())) {
				changed = true;
				break;
			}
		}
		if (!key.reset()) {
			running.set(false);
		}
		return changed;
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
					eventManager.showToastAsync(settings.getAppTitle(), "SWToR Loadingscreen restored");
				}
				logger.info(() -> "Loading screen restored");
			} catch (IOException _) {
				logger.error(() -> "Unable to restore loadingscreen");
			}
		} else {
			logger.warn(() -> "Unable to restore loading screen: Either source (" + source + ") or destination ("
					+ destination + ") does not exist");
		}
	}

}

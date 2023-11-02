package dev.tim9h.rcp.swtor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Injector;

import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.spi.CCard;
import dev.tim9h.rcp.spi.Mode;
import dev.tim9h.rcp.spi.TreeNode;
import dev.tim9h.rcp.swtor.loadingscreen.LoadingScreenWatcher;
import dev.tim9h.rcp.swtor.logpurger.CombatLogPurger;
import dev.tim9h.swtor.parser.CombatLogWatcher;
import dev.tim9h.swtor.parser.bean.CombatLog;

public class SwtorView implements CCard {

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

	@Inject
	public SwtorView(Injector injector) {
		injector.injectMembers(this);
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
		combatLogWatcher.stopWatching();
	}

	@Override
	public Optional<TreeNode<String>> getModelessCommands() {
		var tree = new TreeNode<>(StringUtils.EMPTY);
		tree.add(COMBATLOGS).add(PURGE);
		return Optional.of(tree);
	}

	@Override
	public void initBus(EventManager em) {
		CCard.super.initBus(eventManager);
		em.listen(COMBATLOGS, this::handleCommands);
	}

	private void handleCommands(Object[] data) {
		if (data != null && PURGE.equals(data[0])) {
			eventManager.showWaitingIndicator();
			CompletableFuture.runAsync(() -> combatLogPurger.deleteCombatLogs());
		}
	}

	private void acceptLog(CombatLog log) {
		if ("AreaEntered".equals(log.getEffect().getEvent()) && StringUtils.isNotBlank(log.getEffect().getArea())) {
			logger.info(() -> String.format("%s entered %s", log.getSource().getName(), log.getEffect().getArea()));
			eventManager.showToast(log.getEffect().getEffect(), log.getEffect().getArea());
		}
	}

}

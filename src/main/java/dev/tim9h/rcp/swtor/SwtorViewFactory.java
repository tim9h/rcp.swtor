package dev.tim9h.rcp.swtor;

import java.util.Map;

import com.google.inject.Inject;

import dev.tim9h.rcp.spi.Plugin;
import dev.tim9h.rcp.spi.PluginFactory;

public class SwtorViewFactory implements PluginFactory {

	public static final String SETTINGR_INSTALLATION_LOCATION = "swtor.installation.location";

	public static final String SETTINGR_LOADINGSCREEN_LOCATION = "swtor.loadingscreen.location";

	public static final String SETTINGR_COMBATLOGS_MAXAGE = "swtor.combatlogs.maxage";

	public static final String SETTINGR_COMBATLOGS_DELETIONTIME = "swtor.combatlogs.deletiontime";

	@Inject
	private SwtorView view;

	@Override
	public String getId() {
		return "swtor";
	}

	@Override
	public Plugin create() {
		return view;
	}

	@Override
	public Map<String, String> getSettingsContributions() {
		return Map.of(SETTINGR_INSTALLATION_LOCATION, "C:\\games\\SWToR", SETTINGR_LOADINGSCREEN_LOCATION, "",
				SETTINGR_COMBATLOGS_MAXAGE, "5", SETTINGR_COMBATLOGS_DELETIONTIME, "23:50");
	}

}

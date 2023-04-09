module rcp.swtor {
	exports dev.tim9h.rcp.swtor;

	requires transitive rcp.api;
	requires transitive com.google.guice;
	requires org.apache.logging.log4j;
	requires transitive javafx.controls;
	requires java.desktop;
	requires org.apache.commons.io;
	requires org.apache.commons.lang3;
	requires jPowerShell;
	requires swtor.parser;
}
package eu.semaine.components.dialogue.datastructures;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import eu.semaine.util.SEMAINEUtils;

public class DMProperties
{
	private static DMProperties dmProperties;
	private static Properties props;

	private DMProperties()
	{
		props = new Properties();
		File configFile = SEMAINEUtils.getConfigFile("semaine.DM-config");

		if (configFile == null) {
			throw new Error("No character config file given in property 'semaine.DM-config' -- aborting.");
		}
		try {
			FileInputStream is = new FileInputStream(configFile);
			props.load(is);

		} catch (IOException ioe) {
			throw new Error("Cannot load stateinfo config file from "+configFile.toString(), ioe);
		}
	}

	private static String getProperty( String key )
	{
		if( dmProperties == null ) {
			dmProperties = new DMProperties();
		}
		return props.getProperty(key);
	}

	public static String getResponseFile()
	{
		String f = getProperty("response_file");
		if( f == null ) {
			f = "/eu/semaine/components/dialogue/data/responses.xml";
		}
		return f;
	}

	public static ArrayList<String> getTemplateFiles()
	{
		String f = getProperty("template_files");
		if( f == null ) {
			// TODO: Fixen.
			return new ArrayList<String>();
		}
		ArrayList<String> files = new ArrayList<String>(Arrays.asList(f.split("\\|")));
		return files;
	}

	/* +++ Logging +++ */
	public static boolean getLogging()
	{
		Boolean b = Boolean.parseBoolean(getProperty("logging"));
		if( b == null ) {
			b = true;
		}
		return b;
	}
}

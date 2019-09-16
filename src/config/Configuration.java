package config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by thinhhv on 23/07/2014.
 */

public class Configuration {
    private static final String CONF_PATH = "./conf/qsearch-conf.xml";
    private static final Properties prop;

    static {
        prop = new Properties();
        try {
            prop.loadFromXML(new FileInputStream(CONF_PATH));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Configuration() {
    }

    public static String get(String key) {
        return prop.getProperty(key);
    }

    public static void main(String[] args) {
        for (String key : prop.stringPropertyNames()) {
            System.out.println(key + " = " + prop.getProperty(key));
        }
    }
}
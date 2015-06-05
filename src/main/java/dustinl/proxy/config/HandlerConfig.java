package dustinl.proxy.config;

import com.typesafe.config.Config;

/**
 * The type Plugin config.
 */
public class HandlerConfig {

    /** The plugin name. */
    private String name;
    /** The class name. */
    private String className;
    private Config config;

    /**
     * Instantiates a new Plugin config.
     *
     * @param name the plugin name
     * @param className the class name
     * @param config the config of handler
     */
    public HandlerConfig(String name, String className, Config config) {
        this.name = name;
        this.className = className;
        this.config = config;
    }

    /**
     * Gets the plugin name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets plugin class name.
     *
     * @return the class name
     */
    public String getClassName() {
        return className;
    }

    public Config getConfig() {
        return config;
    }
}

package dustinl.proxy.config;

/**
 * The type Plugin config.
 */
public class PluginConfig {

    /** The plugin name. */
    private String name;
    /** The class name. */
    private String className;

    /**
     * Instantiates a new Plugin config.
     *
     * @param name the plugin name
     * @param className the class name
     */
    public PluginConfig(String name, String className) {
        this.name = name;
        this.className = className;
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
}

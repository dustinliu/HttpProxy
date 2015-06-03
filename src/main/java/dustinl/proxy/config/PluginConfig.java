package dustinl.proxy.config;

public class PluginConfig {

    private String name;
    private String className;

    public PluginConfig(String name, String className) {
        this.name = name;
        this.className = className;
    }

    public String getName() {
        return name;
    }

    public String getClassName() {
        return className;
    }
}

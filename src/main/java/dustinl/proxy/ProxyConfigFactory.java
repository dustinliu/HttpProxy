package dustinl.proxy;

/**
 * The factory to get config singleton.
 */
public final class ProxyConfigFactory {
    private static final ProxyConfig CONFIG = new  TypeSafeProxyConfig();

    /**
     * Gets config.
     *
     * @return the config
     */
    public static ProxyConfig getConfig() {
        return CONFIG;
    }

    private ProxyConfigFactory() {

    }
}

package dustinl.proxy;

public class ProxyConfigFactory {
    private static final ProxyConfig config = new  TypeSafeProxyConfig();

    public static ProxyConfig getConfig() {
        return config;
    }
}

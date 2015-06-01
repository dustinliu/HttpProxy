package dustinl.proxy;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class TypeSafeProxyConfig implements ProxyConfig {
    private Config config;

    TypeSafeProxyConfig() {
        config = ConfigFactory.load();
    }

    @Override
    public int getPort() {
        return config.getInt("port");
    }
}

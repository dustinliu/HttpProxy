package dustinl.proxy;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 *  {@link ProxyConfig} typesafe implementation.
 */
public class TypeSafeProxyConfig implements ProxyConfig {
    private Config config;

    TypeSafeProxyConfig() {
        config = ConfigFactory.load();
    }

    @Override
    public int getPort() {
        return config.getInt("port");
    }

    @Override
    public int getBossThreads() {
        return config.getInt("bossThreads");
    }

    @Override
    public int getWorkerThreads() {
        return config.getInt("workerThreads");
    }
}

package dustinl.proxy.config;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    public List<HandlerConfig> getHandlerConfigs() {
        List<HandlerConfig> configs = new ArrayList<>();
        List<? extends Config> handlers = config.getConfigList("handlers");
        for (Config handler : handlers) {
            configs.add(new HandlerConfig(handler.getString("name"), handler.getString("className"),
                    getConfig(handler)));
        }
        return configs;
    }

    private static Config getConfig(Config config) {
        Config ret;
        try {
            ret = config.getConfig("config");
        } catch (Exception e) {
            ret = null;
        }

        return ret;
    }
}

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
    public List<PluginConfig> getPluginConfigs() {
        List<PluginConfig> configs = new ArrayList<>();
        List<? extends Config> plugins = config.getConfigList("plugins");
        for (Config plugin : plugins) {
            configs.add(new PluginConfig(plugin.getString("name"), plugin.getString("className")));
        }
        return configs;
    }
}

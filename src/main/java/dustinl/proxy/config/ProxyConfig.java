package dustinl.proxy.config;

import java.util.List;

/**
 * The interface Proxy config.
 */
public interface ProxyConfig {
    /**
     * Gets port.
     *
     * @return the port
     */
    int getPort();

    /**
     * Gets number of boss threads.
     *
     * @return number of boss threads
     */
    int getBossThreads();

    /**
     * Gets number of worker threads.
     *
     * @return number of worker threads
     */
    int getWorkerThreads();

    List<PluginConfig> getPluginConfigs();
}

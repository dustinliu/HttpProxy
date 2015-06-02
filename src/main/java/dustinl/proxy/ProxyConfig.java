package dustinl.proxy;

public interface ProxyConfig {
    int getPort();

    int getBossThreads();

    int getWorkerThreads();
}

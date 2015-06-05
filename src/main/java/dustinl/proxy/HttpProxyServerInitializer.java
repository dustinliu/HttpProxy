package dustinl.proxy;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.typesafe.config.Config;
import dustinl.proxy.config.HandlerConfig;
import dustinl.proxy.config.ProxyConfigFactory;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Http proxy server initializer.
 */
public class HttpProxyServerInitializer extends ChannelInitializer<SocketChannel> {

    private static boolean inited = false;
    private static Map<String, Class<ChannelHandler>> handlerMap;
    private static Lock lock = new ReentrantLock();
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProxyServerInitializer.class);

    @SuppressWarnings("unchecked")
    private static void initHandlers()
           throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (!inited) {
            lock.lock();
            if (!inited) {
                try {
                    handlerMap = new HashMap<>();
                    List<HandlerConfig> handlerConfigs = ProxyConfigFactory.getConfig().getHandlerConfigs();
                    for (HandlerConfig handlerConfig : handlerConfigs) {
                        Class handler = Class.forName(handlerConfig.getClassName());
                        handlerMap.put(handlerConfig.getName(), handler);
                    }
                    inited = true;
                } catch (Exception e) {
                    LOGGER.error("instantiate handler failed", e);
                    throw e;
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    public void initChannel(SocketChannel ch)
            throws ClassNotFoundException, IllegalAccessException,
            InstantiationException, NoSuchMethodException, InvocationTargetException {
        initHandlers();

        ChannelPipeline p = ch.pipeline();
        List<HandlerConfig> handlerConfigs = ProxyConfigFactory.getConfig().getHandlerConfigs();
        for (HandlerConfig handlerConfig : handlerConfigs) {
            String name = handlerConfig.getName();
            Config config = handlerConfig.getConfig();
            ChannelHandler handler = null;
            if (config == null) {
                handler = handlerMap.get(name).newInstance();
            } else {
                handler = ConstructorUtils.invokeConstructor(handlerMap.get(name), config);
            }
            p.addLast(name, handler);
        }
    }
}

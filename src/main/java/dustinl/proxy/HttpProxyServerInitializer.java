package dustinl.proxy;

import java.util.List;

import dustinl.proxy.config.PluginConfig;
import dustinl.proxy.config.ProxyConfig;
import dustinl.proxy.config.ProxyConfigFactory;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

/**
 * Http proxy server initializer.
 */
public class HttpProxyServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final String HTTP_CODEC_NAME = "httpCodec";
    private static final String HTTP_EXEC_NAME = "httpExec";
    private static final String HTTP_WRITER_NAME = "httpWriter";
    private static ProxyConfig config = ProxyConfigFactory.getConfig();

    @Override
    public void initChannel(SocketChannel ch)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {

        ChannelPipeline p = ch.pipeline();
        List<PluginConfig> plugins = config.getPluginConfigs();
        for (PluginConfig plugin : plugins) {
            ChannelHandler handler = (ChannelHandler) Class.forName(plugin.getClassName()).newInstance();
            p.addLast(plugin.getName(), handler);
        }
    }
}

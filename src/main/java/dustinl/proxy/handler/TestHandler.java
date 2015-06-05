package dustinl.proxy.handler;

import com.typesafe.config.Config;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The type Test handler.
 */
public class TestHandler extends SimpleChannelInboundHandler<HttpRequest> {

    /** The App id. */
    private String appId;

    /** logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(TestHandler.class);

    /**
     * Instantiates a new Test handler.
     *
     * @param config the config
     */
    public TestHandler(Config config) {
        appId = config.getString("appId");
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) {
        LOGGER.debug("TestHandler appid: " + appId);
        ctx.fireChannelRead(msg);
    }
}

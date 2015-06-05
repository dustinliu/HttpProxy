package dustinl.proxy.handler;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.typesafe.config.Config;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The type Ip acl handler.
 */
public class AccessControlHandler extends ChannelInboundHandlerAdapter {
    /** The Inited. */
    private static boolean inited = false;
    /** The constant lock. */
    private static Lock lock = new ReentrantLock();
    /** The ip allowed to access the proxy */
    private static Set<String> allowedIpTable = new HashSet<>();
    /** LOGGER. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AccessControlHandler.class);

    /**
     * Instantiates a new Ip acl handler.
     *
     * @param config the config
     */
    public AccessControlHandler(Config config) {
        init(config);
    }

    private void init(Config config) {
        if (!inited) {
            lock.lock();
            if (!inited) {
                List<String> ips = config.getStringList("acls");
                for (String ip : ips) {
                    allowedIpTable.add(ip);
                }
                inited = true;
            }
            lock.unlock();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        String ip = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
        if (!allowedIpTable.contains(ip)) {
            LOGGER.debug("ip " + ip + " not in acl, close connection");
            ctx.close();
        } else {
            super.channelActive(ctx);
        }
    }
}

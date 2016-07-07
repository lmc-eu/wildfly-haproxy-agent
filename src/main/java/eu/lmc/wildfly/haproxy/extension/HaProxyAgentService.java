package eu.lmc.wildfly.haproxy.extension;

import org.jboss.as.network.SocketBinding;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

import java.util.concurrent.ThreadFactory;

/**
 * The service itself.
 * <p/>
 * The injection code is copied from https://github.com/danbev/netty-subsystem/tree/master/subsystem
 */
public class HaProxyAgentService implements Service<HaProxyAgentService> {

    private final Logger logger = Logger.getLogger(HaProxyAgentService.class);

    private final InjectedValue<SocketBinding> injectedSocketBinding = new InjectedValue<>();
    private final InjectedValue<ThreadFactory> injectedThreadFactory = new InjectedValue<>();
    private final String source;

    public HaProxyAgentService(String source) {
        this.source = source;
    }

    @Override
    public HaProxyAgentService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public InjectedValue<SocketBinding> getInjectedSocketBinding() {
        return injectedSocketBinding;
    }

    public InjectedValue<ThreadFactory> getInjectedThreadFactory() {
        return injectedThreadFactory;
    }

    public static ServiceName createServiceName(final String source) {
        return ServiceName.JBOSS.append("haproxy-agent", source);
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        final ThreadFactory threadFactory = injectedThreadFactory.getOptionalValue();
        final SocketBinding socketBinding = injectedSocketBinding.getOptionalValue();
        logger.info("haproxy agent for " + source + ", binding to port " + socketBinding.getPort());
        logger.info("thread factory: " + threadFactory);
        //TODO: bind!
    }

    @Override
    public void stop(StopContext stopContext) {
        logger.info("haproxy agent for " + source + " shutting down");
    }

}

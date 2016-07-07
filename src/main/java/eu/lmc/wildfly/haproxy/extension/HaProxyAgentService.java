package eu.lmc.wildfly.haproxy.extension;

import org.jboss.as.network.SocketBinding;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.threads.JBossThreadPoolExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * The service itself.
 * <p/>
 * The injection code is copied from https://github.com/danbev/netty-subsystem/tree/master/subsystem
 */
public class HaProxyAgentService implements Service<HaProxyAgentService> {

    private final Logger logger = Logger.getLogger(HaProxyAgentService.class);

    private final InjectedValue<SocketBinding> injectedSocketBinding = new InjectedValue<>();
    private final String name;
    private final String source;
    private final int poolSize;

    private ExecutorService executor;

    public HaProxyAgentService(String name, String source, int poolSize) {
        this.name = name;
        this.source = source;
        this.poolSize = poolSize;
    }

    public String getName() {
        return name;
    }

    public int getPoolSize() {
        return poolSize;
    }

    @Override
    public HaProxyAgentService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public InjectedValue<SocketBinding> getInjectedSocketBinding() {
        return injectedSocketBinding;
    }

    public static ServiceName createServiceName(final String source) {
        return ServiceName.JBOSS.append("haproxy-agent", source);
    }

    @Override
    public synchronized void start(StartContext startContext) throws StartException {
        final SocketBinding socketBinding = injectedSocketBinding.getOptionalValue();
        logger.info("haproxy agent " + getName() + " for  " + source + ", binding to port " + socketBinding.getPort());
        executor = new JBossThreadPoolExecutor(1, getPoolSize(),
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>()
        );
        logger.info("executor: " + executor);

        //TODO: bind!
    }

    @Override
    public synchronized void stop(StopContext stopContext) {
        logger.info("haproxy agent " + getName() + " shutting down");
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

}

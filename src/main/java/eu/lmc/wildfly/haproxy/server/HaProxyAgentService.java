package eu.lmc.wildfly.haproxy.server;

import eu.lmc.wildfly.haproxy.extension.ServerDefinition;
import org.jboss.as.network.SocketBinding;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.threads.JBossThreadFactory;
import org.xnio.XnioWorker;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The service itself.
 * <p/>
 * The injection code is copied from https://github.com/danbev/netty-subsystem/tree/master/subsystem
 */
public class HaProxyAgentService implements Service<HaProxyAgentService> {

    private final Logger logger = Logger.getLogger(HaProxyAgentService.class);

    private final InjectedValue<SocketBinding> injectedSocketBinding = new InjectedValue<>();
    private final InjectedValue<XnioWorker> injectedXnioWorker = new InjectedValue<>();
    private final String name;
    private final String source;

    private AbstractAgentCheckServer server;

    public HaProxyAgentService(String name, String source) {
        this.name = name;
        this.source = source;
    }

    public String getName() {
        return name;
    }

    @Override
    public HaProxyAgentService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    /**
     * Socket binding holder: used to fill dependency in subsystem handler.
     */
    public InjectedValue<SocketBinding> getInjectedSocketBinding() {
        return injectedSocketBinding;
    }

    /**
     * Socket binding holder: used to fill dependency in subsystem handler.
     */
    public InjectedValue<XnioWorker> getInjectedXnioWorker() {
        return injectedXnioWorker;
    }

    public static ServiceName createServiceName(final String source) {
        return ServiceName.JBOSS.append("haproxy-agent", source);
    }

    @Override
    public synchronized void start(StartContext startContext) throws StartException {
        final SocketBinding socketBinding = injectedSocketBinding.getOptionalValue();
        final int port = socketBinding != null ? socketBinding.getPort() : ServerDefinition.DEFAULT_PORT;
        final InetAddress bindAddr = socketBinding != null ? socketBinding.getAddress() : null;
        logger.info("haproxy agent " + getName() + " for  " + source + ", binding to port " + port);

        startXnio(port, bindAddr, source);
    }

    private void startXnio(int port, InetAddress bindAddr, String source) throws StartException {
        Optional<File> file = Optional.empty();
        Optional<URI> uri = Optional.empty();

        if (source.matches("^https?://.*")) {
            try {
                final URI value = new URL(source).toURI();
                uri = Optional.of(value);
            } catch (URISyntaxException|MalformedURLException ignored) {
                //ok, it's obviously not URL
            }
        }
        if (!uri.isPresent()) {
            file = Optional.of(new File(source));
        }

        try {
            final XnioWorker xnio = getInjectedXnioWorker().getValue();
            server = new XnioAgentCheckServer(xnio, file, uri);
            server.start(bindAddr, port);
        } catch (IOException e) {
            logger.error("failed to start...", e);
            throw new StartException(e);
        }
    }

    @Override
    public synchronized void stop(StopContext stopContext) {
        logger.info("haproxy agent " + getName() + " shutting down");
        if (server != null) {
            try {
                server.close();
            } catch (IOException e) {
                logger.error("failed to stop agent server", e);
            }
            server = null;
        }
    }

}

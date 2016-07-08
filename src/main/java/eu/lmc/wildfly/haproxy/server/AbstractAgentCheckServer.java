package eu.lmc.wildfly.haproxy.server;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;

/**
 * Base superclass of agent check server.
 */
abstract class AbstractAgentCheckServer implements Closeable {

    /**
     * Default state to send.
     */
    protected static final byte[] DEFAULT_STATE = "ready\n".getBytes();


    private int maxSize = 100;
    private int timeoutSeconds = 4;

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Start listening.
     *
     * @param listenAddress address to listen on, <code>null</code> = any interface
     * @param port          port to listen on
     * @throws IOException error binding port
     * @see java.net.InetSocketAddress#InetSocketAddress(InetAddress, int)
     */
    public abstract void start(InetAddress listenAddress, int port) throws IOException;
}

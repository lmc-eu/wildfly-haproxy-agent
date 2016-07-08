package eu.lmc.wildfly.haproxy.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plain SDK NIO implementation of haproxy-agent:
 * Start raw TCP socket that writes status from static file (or just default value).
 * <p/>
 * Makes its own thread pool, of configured size.
 */
class NioAgentCheckServer extends AbstractAgentCheckServer {

    private final static Logger logger = Logger.getLogger(NioAgentCheckServer.class.getName());

    protected final ThreadFactory threadFactory;
    protected final int threadPoolSize;
    protected final Path filename;

    private AsynchronousServerSocketChannel channel;
    private AsynchronousChannelGroup asyncGroup;

    public NioAgentCheckServer(ThreadFactory threadFactory, int threadPoolSize, Path filename) throws IOException {
        this.threadFactory = threadFactory;
        this.threadPoolSize = threadPoolSize;
        this.filename = filename;
        asyncGroup = AsynchronousChannelGroup.withFixedThreadPool(threadPoolSize, threadFactory);
    }

    @Override
    public void start(InetAddress listenAddress, int port) throws IOException {
        channel = AsynchronousServerSocketChannel.open(asyncGroup);
        channel.bind(new InetSocketAddress(listenAddress, port));
        channel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel stream, Void attachment) {
                channel.accept(null, this);

                ByteBuffer responseBuffer;
                try {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("accepted connection from: " + stream.getRemoteAddress());
                    }

                    final FileChannel file = FileChannel.open(filename, StandardOpenOption.READ);
                    int size = file.size() > getMaxSize() ? getMaxSize() : (int) file.size();
                    responseBuffer = ByteBuffer.allocate(size);
                    while (responseBuffer.hasRemaining() && file.read(responseBuffer) >= 0) {
                    }
                    responseBuffer.rewind();
                } catch (IOException e) {
                    //ok, no file
                    responseBuffer = ByteBuffer.wrap(DEFAULT_STATE);
                }
                if (logger.isLoggable(Level.FINER)) {
                    logger.finer("writing: " + responseBuffer);
                }
                new ResponseHandler(stream, getTimeoutSeconds(), TimeUnit.SECONDS)
                        .startWriting(responseBuffer);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                //asynchronous close is not really error
                if (!(exc instanceof AsynchronousCloseException)) {
                    logger.log(Level.INFO, "failed to accept", exc);
                }
            }
        });
    }

    @Override
    public void close() {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                //this is not really interesting... might be just TCP connection error
                logger.log(Level.FINE, "failed to close channel", e);
            }
        }
        if (asyncGroup != null) {
            try {
                asyncGroup.shutdownNow();
                asyncGroup = null;
            } catch (IOException e) {
                //this, however, is noteworthy
                logger.log(Level.INFO, "failed to close async group", e);
            }
        }
    }

    /**
     * Correct handler to asynchronously write data to asynchronous channel. Usually not needed, but in theory
     * even small texts could be split to several write() invocations...
     */
    private static class ResponseHandler implements CompletionHandler<Integer, ByteBuffer> {
        private final AsynchronousSocketChannel out;
        private final int timeout;
        private final TimeUnit timeoutUnits;

        public ResponseHandler(AsynchronousSocketChannel stream, int timeout, TimeUnit timeoutUnits) {
            this.out = stream;
            this.timeout = timeout;
            this.timeoutUnits = timeoutUnits;
        }

        void startWriting(ByteBuffer buf) {
            out.write(buf, timeout, timeoutUnits, buf, this);
        }

        @Override
        public void completed(Integer result, ByteBuffer buf) {
            if (logger.isLoggable(Level.FINER)) {
                logger.finer(result + "B written, buffer: " + buf);
            }
            if (buf.hasRemaining()) {
                logger.fine("continuing writing");
                startWriting(buf);
            } else {
                logger.fine("closing stream");
                close();
            }
        }

        @Override
        public void failed(Throwable exc, ByteBuffer buf) {
            logger.log(Level.INFO, "write failed", exc);
            close();
        }

        private void close() {
            try {
                out.close();
            } catch (IOException e) {
                logger.log(Level.INFO, "failed to close", e);
            }
        }
    }
}

package eu.lmc.wildfly.haproxy.server;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.methods.AsyncByteConsumer;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;
import org.jboss.threads.JBossThreadFactory;
import org.xnio.ChannelListener;
import org.xnio.FileAccess;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.NoSuchFileException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.xnio.IoUtils.safeClose;

/**
 * XNIO implementation of haproxy-agent:
 * Start raw TCP socket that writes status from static file (or just default value).
 */
class XnioAgentCheckServer extends AbstractAgentCheckServer {

    private final static Logger logger = Logger.getLogger(XnioAgentCheckServer.class.getName());

    protected final XnioWorker worker;
    /**
     * Filename; optional, might be completely skipped.
     */
    protected final Optional<File> filename;
    protected final Optional<URI> httpUri;

    private AcceptingChannel<StreamConnection> server;

    /**
     * Optional: http client.
     */
    private CloseableHttpAsyncClient httpAsyncClient;
    private HttpAsyncRequestProducer httpRequestProducer;

    public XnioAgentCheckServer(XnioWorker worker, Optional<File> filename, Optional<URI> httpUri) {
        this.worker = worker;
        this.filename = filename;
        this.httpUri = httpUri;
    }

    protected XnioWorker getWorker() {
        return worker;
    }

    public Xnio getXnio() {
        return getWorker().getXnio();
    }

    @Override
    public void close() {
        safeClose(server);
        safeClose(httpAsyncClient);
    }

    @Override
    public void start(InetAddress listenAddress, int port) throws IOException {

        if (httpUri.isPresent()) {
            httpAsyncClient = HttpAsyncClientBuilder.create()
                    .disableCookieManagement()
                    .setThreadFactory(new JBossThreadFactory(null, true, null, "httpAsync-%i", null, null))
                    .build();
            httpAsyncClient.start();
            httpRequestProducer = HttpAsyncMethods.createGet(httpUri.get());
        }


        ChannelListener<StreamSinkChannel> writeListener = channel -> {
            if (copyFile(channel))
                return;

            ByteBuffer responseBuffer = ByteBuffer.wrap(DEFAULT_STATE);
            try {
                if (logger.isLoggable(Level.FINER)) {
                    logger.finer("writing: " + responseBuffer);
                }
                channel.writeFinal(responseBuffer);
            } catch (IOException e) {
                logger.log(Level.INFO, "failed to write response", e);
            }
        };

        ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = bindChannel -> {
            StreamConnection accepted;
            // channel is ready to accept zero or more connections
            while (true) {
                try {
                    if ((accepted = bindChannel.accept()) == null)
                        break;
                } catch (IOException ignored) {
                    break;
                }
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("accepted " + accepted.getPeerAddress());
                }
                final ConduitStreamSinkChannel sinkChannel = accepted.getSinkChannel();
                //URI is special because async
                if (startURI(sinkChannel))
                    return;
                else {
                    sinkChannel.getWriteSetter().set(writeListener);
                    sinkChannel.resumeWrites();
                }
            }
        };

        server = worker.createStreamConnectionServer(
                new InetSocketAddress(listenAddress, port),
                acceptListener,
                OptionMap.EMPTY
        );
        // lets start accepting connections
        server.resumeAccepts();

        logger.log(Level.INFO, "listening on " + server.getLocalAddress());
    }

    /**
     * File implementation: if present, copy file content to specified channel.
     *
     * @param channel channel to write file content to
     * @return true when copied; false when no file is configured, present or something failed
     */
    private boolean copyFile(WritableByteChannel channel) {
        if (!filename.isPresent()) {
            return false;
        }
        try {
            final FileChannel fc = getXnio().openFile(filename.get(), FileAccess.READ_ONLY);
            final long fileSize = fc.size();
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("writing from file: " + filename.get() + " " + fileSize + "B");
            }
            fc.transferTo(0, Math.min(fileSize, getMaxSize()), channel);
            fc.close();
            safeClose(channel);
            return true;
        } catch (NoSuchFileException | FileNotFoundException ignored) {
        } catch (IOException e) {
            logger.log(Level.INFO, "error reading " + filename, e);
        }
        return false;
    }

    /**
     * URI implementation: if present, copy file content to specified channel.
     *
     * @param channel channel to write file content to
     * @return true when copied; false when no file is configured, present or something failed
     */
    private boolean startURI(StreamSinkChannel channel) {
        if (httpRequestProducer == null) {
            return false;
        }

        AsyncByteConsumer<HttpResponse> consumer = new AsyncByteConsumer<HttpResponse>() {
            private volatile HttpResponse response;
            private volatile int count;
            private volatile boolean stop = false;

            @Override
            protected void onByteReceived(ByteBuffer buf, IOControl ioctrl) throws IOException {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("onByteReceived: " + buf);
                }
                if (stop || !channel.isOpen()) {
                    ioctrl.shutdown();
                    return;
                }
                count += channel.write(buf);
                if (buf.remaining() + count > getMaxSize()) {
                    channel.shutdownWrites();
                    ioctrl.shutdown();
                } else {
                    channel.resumeWrites();
                }
            }

            @Override
            protected void onResponseReceived(HttpResponse response) throws HttpException, IOException {
                this.response = response;
                final StatusLine statusLine = response.getStatusLine();
                final int statusCode = statusLine.getStatusCode();
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("onResponseReceived: " + response);
                }
                if (statusCode >= 300) {
                    logger.info("non-OK status code received: " + statusCode + " " + statusLine.getReasonPhrase());
                    if (channel.isOpen()) {
                        channel.writeFinal(ByteBuffer.wrap(DEFAULT_STATE));
                        channel.shutdownWrites();
                    }
                    stop = true;
                }
            }

            @Override
            protected HttpResponse buildResult(HttpContext context) throws Exception {
                return response;
            }
        };

        final FutureCallback<HttpResponse> callback = new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse result) {
                safeClose(channel);
            }

            @Override
            public void failed(Exception ex) {
                logger.log(Level.INFO, "error reading from http", ex);
                safeClose(channel);
            }

            @Override
            public void cancelled() {
                logger.log(Level.INFO, "http reading cancelled");
                safeClose(channel);
            }
        };
        httpAsyncClient.execute(httpRequestProducer, consumer, callback);

        return true;
    }

}

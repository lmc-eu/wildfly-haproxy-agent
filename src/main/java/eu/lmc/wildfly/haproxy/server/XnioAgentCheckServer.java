package eu.lmc.wildfly.haproxy.server;

import org.xnio.ChannelListener;
import org.xnio.FileAccess;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.streams.ChannelOutputStream;
import org.xnio.streams.Streams;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
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
    }

    @Override
    public void start(InetAddress listenAddress, int port) throws IOException {
        ChannelListener<StreamSinkChannel> writeListener = channel -> {
            if (copyFile(channel))
                return;
            if (copyURI(channel))
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
                sinkChannel.getWriteSetter().set(writeListener);
                sinkChannel.resumeWrites();
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
     * @param channel        channel to write file content to
     * @return true when copied; false when no file is configured, present or something failed
     */
    private boolean copyURI(StreamSinkChannel channel) {
        if (!httpUri.isPresent()) {
            return false;
        }
        final URL url;
        try {
            url = httpUri.get().toURL();
        } catch (MalformedURLException e) {
            //ok, not a valid URI..
            return false;
        }

        getWorker().submit(() -> {
            try {
                final URLConnection connection = url.openConnection();
                connection.setReadTimeout(getTimeoutSeconds() * 1000 / 2);
                connection.setConnectTimeout(getTimeoutSeconds() * 1000 / 2);
                final InputStream is = connection.getInputStream();
                Streams.copyStream(is, new ChannelOutputStream(channel), true);
            } catch (IOException e) {
                logger.log(Level.INFO, "failed to proxy url", e);
            } finally {
                safeClose(channel);
            }
        });

        return true;
    }

}

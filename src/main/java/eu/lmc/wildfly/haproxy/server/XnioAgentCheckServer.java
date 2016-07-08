package eu.lmc.wildfly.haproxy.server;

import org.xnio.ChannelListener;
import org.xnio.FileAccess;
import org.xnio.IoUtils;
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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.NoSuchFileException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private AcceptingChannel<StreamConnection> server;

    public XnioAgentCheckServer(XnioWorker worker, Optional<File> filename) {
        this.worker = worker;
        this.filename = filename;
    }

    protected XnioWorker getWorker() {
        return worker;
    }

    public Xnio getXnio() {
        return getWorker().getXnio();
    }

    @Override
    public void close() {
        IoUtils.safeClose(server);
    }

    @Override
    public void start(InetAddress listenAddress, int port) throws IOException {
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
            IoUtils.safeClose(channel);
            return true;
        } catch (NoSuchFileException | FileNotFoundException ignored) {
        } catch (IOException e) {
            logger.log(Level.INFO, "error reading " + filename, e);
        }
        return false;
    }

}

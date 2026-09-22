package dev.zomboidds.bridge.server;

import dev.zomboidds.bridge.Log;
import dev.zomboidds.bridge.asset.IconService;
import dev.zomboidds.bridge.state.ClientConnection;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * One port serving both the WebSocket ({@code /ws}) and plain HTTP ({@code /icons}, {@code /health}).
 * Runs on NanoHTTPD's own daemon threads, never on the game thread.
 */
public final class BridgeServer extends NanoWSD {

    /**
     * Idle sockets are closed after this long. The app pings over the WebSocket every 10 s, so
     * live connections stay open and dead ones are cleaned up.
     */
    private static final int SOCKET_TIMEOUT_MS = 30_000;

    public interface Listener {
        void onConnected(ClientConnection client);

        void onText(ClientConnection client, String text);

        void onDisconnected(ClientConnection client);
    }

    private final Set<ClientSession> clients = new CopyOnWriteArraySet<>();
    private final IconService icons;
    private Listener listener;

    public BridgeServer(String host, int port, IconService icons) {
        super(host, port);
        this.icons = icons;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void startDaemon() throws IOException {
        start(SOCKET_TIMEOUT_MS, true);
        Log.info("listening on " + getHostname() + ":" + getListeningPort());
    }

    public Collection<? extends ClientConnection> clients() {
        return Collections.unmodifiableSet(clients);
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        return new ClientSession(handshake, this);
    }

    @Override
    protected Response serveHttp(IHTTPSession session) {
        if (session.getMethod() != Method.GET) {
            return text(Response.Status.METHOD_NOT_ALLOWED, "GET only");
        }
        String uri = session.getUri();
        if ("/health".equals(uri)) {
            return newFixedLengthResponse(Response.Status.OK, "application/json",
                    "{\"ok\":true,\"clients\":" + clients.size() + "}");
        }
        if (uri.startsWith("/icons/") && uri.endsWith(".png")) {
            String name = uri.substring("/icons/".length(), uri.length() - ".png".length());
            return serveIcon(name);
        }
        return text(Response.Status.NOT_FOUND, "not found");
    }

    private Response serveIcon(String name) {
        Optional<byte[]> png = icons.find(name);
        if (png.isEmpty()) {
            return text(Response.Status.NOT_FOUND, "no icon " + name);
        }
        byte[] bytes = png.get();
        Response response = newFixedLengthResponse(Response.Status.OK, "image/png",
                new ByteArrayInputStream(bytes), bytes.length);
        // Icons never change for a given game install, so let the app cache them for good.
        response.addHeader("Cache-Control", "public, max-age=31536000, immutable");
        return response;
    }

    private static Response text(Response.IStatus status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "text/plain", body);
    }

    void onSessionOpen(ClientSession session) {
        clients.add(session);
        Log.info("client connected: " + session.describe() + " (" + clients.size() + " total)");
        if (listener != null) {
            listener.onConnected(session);
        }
    }

    void onSessionText(ClientSession session, String text) {
        if (listener != null) {
            listener.onText(session, text);
        }
    }

    void onSessionClosed(ClientSession session) {
        if (clients.remove(session)) {
            Log.info("client disconnected: " + session.describe() + " (" + clients.size() + " total)");
            if (listener != null) {
                listener.onDisconnected(session);
            }
        }
    }
}

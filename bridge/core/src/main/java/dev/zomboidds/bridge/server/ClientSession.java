package dev.zomboidds.bridge.server;

import dev.zomboidds.bridge.state.ClientConnection;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoWSD;
import fi.iki.elonen.NanoWSD.WebSocketFrame.CloseCode;

import java.io.IOException;

final class ClientSession extends NanoWSD.WebSocket implements ClientConnection {

    private final BridgeServer server;
    private final String remote;

    ClientSession(IHTTPSession handshake, BridgeServer server) {
        super(handshake);
        this.server = server;
        this.remote = handshake.getRemoteIpAddress();
    }

    @Override
    public void send(String json) {
        if (!isOpen()) {
            return;
        }
        try {
            super.send(json);
        } catch (IOException e) {
            server.onSessionClosed(this);
        }
    }

    @Override
    public String describe() {
        return remote;
    }

    @Override
    protected void onOpen() {
        server.onSessionOpen(this);
    }

    @Override
    protected void onClose(CloseCode code, String reason, boolean initiatedByRemote) {
        server.onSessionClosed(this);
    }

    @Override
    protected void onMessage(NanoWSD.WebSocketFrame message) {
        server.onSessionText(this, message.getTextPayload());
    }

    @Override
    protected void onPong(NanoWSD.WebSocketFrame pong) {
    }

    @Override
    protected void onException(IOException exception) {
        server.onSessionClosed(this);
    }
}

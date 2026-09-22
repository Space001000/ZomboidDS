package dev.zomboidds.bridge.mock;

import dev.zomboidds.bridge.port.AdapterInfo;
import dev.zomboidds.bridge.port.BridgeContext;
import dev.zomboidds.bridge.port.GameAdapter;
import dev.zomboidds.bridge.port.GameEnvironment;
import dev.zomboidds.bridge.port.IconSource;

/** A {@link GameAdapter} backed by {@link MockGame} instead of Project Zomboid. */
final class MockGameAdapter implements GameAdapter {

    private MockGame game;

    MockGame game() {
        return game;
    }

    @Override
    public AdapterInfo info() {
        return new AdapterInfo("mock", "Fake game for companion app development");
    }

    @Override
    public IconSource createIconSource(GameEnvironment env) {
        return new PlaceholderIconSource();
    }

    @Override
    public void attach(GameEnvironment env, BridgeContext context) {
        game = new MockGame(context);
        game.start();
    }
}

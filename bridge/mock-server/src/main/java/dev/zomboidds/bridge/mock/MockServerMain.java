package dev.zomboidds.bridge.mock;

import dev.zomboidds.bridge.DefaultGameEnvironment;

/**
 * Runs the real bridge with a fake game behind it, so the companion app can be developed without
 * Project Zomboid.
 *
 * <pre>
 *   ./gradlew :bridge:mock-server:run
 *   ./gradlew :bridge:mock-server:run -Pmock.args="inventory=full,gameDir=C:/path/to/ProjectZomboid"
 *   adb reverse tcp:7786 tcp:7786     # lets an attached device reach it on 127.0.0.1
 * </pre>
 * The Android emulator can reach it at 10.0.2.2 instead.
 */
public final class MockServerMain {

    public static void main(String[] args) throws Exception {
        MockServer server = MockServer.start(DefaultGameEnvironment.parseOptions(args.length > 0 ? args[0] : ""));
        try {
            server.runConsole();
        } finally {
            server.stop();
        }
    }

    private MockServerMain() {
    }
}

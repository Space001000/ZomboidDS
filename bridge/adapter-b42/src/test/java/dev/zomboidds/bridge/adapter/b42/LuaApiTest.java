package dev.zomboidds.bridge.adapter.b42;

import dev.zomboidds.bridge.port.BridgeContext;
import dev.zomboidds.bridge.port.CommandSource;
import dev.zomboidds.bridge.port.StatePublisher;
import org.junit.jupiter.api.Test;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.vm.KahluaTable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaApiTest {

    private final J2SEPlatform platform = J2SEPlatform.getInstance();
    private final List<String> published = new ArrayList<>();
    private final LuaApi api = new LuaApi(new BridgeContext() {
        @Override
        public StatePublisher state() {
            return new StatePublisher() {
                @Override
                public void publish(String type, Object data) {
                    published.add(type);
                }

                @Override
                public void clearRetained() {
                }
            };
        }

        @Override
        public CommandSource commands() {
            return max -> List.of();
        }

        @Override
        public int connectedClients() {
            return 1;
        }
    }, new KahluaConverter(() -> platform));

    private KahluaTable inventory(String... names) {
        KahluaTable items = platform.newTable();
        for (int i = 0; i < names.length; i++) {
            KahluaTable item = platform.newTable();
            item.rawset("name", names[i]);
            item.rawset("weight", 0.5);
            items.rawset((double) (i + 1), item);
        }
        KahluaTable table = platform.newTable();
        table.rawset("items", items);
        return table;
    }

    @Test
    void sendsOnlyWhatChanged() {
        assertTrue(api.emitIfChanged("inventory", inventory("Axe", "Apple")), "the first one is sent");
        assertFalse(api.emitIfChanged("inventory", inventory("Axe", "Apple")), "an equal one (a new table) isn't");
        assertTrue(api.emitIfChanged("inventory", inventory("Apple", "Axe")), "a different order is a change");
        assertTrue(api.emitIfChanged("containers", inventory("Apple", "Axe")), "each type is compared on its own");
        assertEquals(List.of("inventory", "inventory", "containers"), published);
    }

    @Test
    void comparesWithoutSending() {
        assertFalse(api.sameAsLast("worldMenu", inventory("Open door")), "nothing to compare with yet");
        assertTrue(api.sameAsLast("worldMenu", inventory("Open door")), "the same options again");
        assertFalse(api.sameAsLast("worldMenu", inventory("Close door")), "changed");
        assertTrue(api.sameAsLast("worldMenu", inventory("Close door")), "and remembered");
        api.reset();
        assertFalse(api.sameAsLast("worldMenu", inventory("Close door")), "a new game forgets");
        assertEquals(List.of("session"), published, "nothing sent but the reset's session");
    }

    @Test
    void sendsEverythingAgainAfterForgetting() {
        api.emitIfChanged("inventory", inventory("Axe"));
        api.forgetSent();
        assertTrue(api.emitIfChanged("inventory", inventory("Axe")), "a client connected: sent again");
        api.reset();
        assertTrue(api.emitIfChanged("inventory", inventory("Axe")), "a new game: sent again");
    }
}

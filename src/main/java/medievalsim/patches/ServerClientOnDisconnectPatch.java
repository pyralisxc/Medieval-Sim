package medievalsim.patches;
import medievalsim.zones.ProtectedZoneTracker;
import medievalsim.zones.PvPZoneTracker;
import medievalsim.zones.SettlementProtectionTracker;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import net.bytebuddy.asm.Advice;

public class ServerClientOnDisconnectPatch {

    @ModMethodPatch(target=ServerClient.class, name="onDisconnect", arguments={})
    public static class OnDisconnect {
        @Advice.OnMethodExit
        static void onExit(@Advice.This ServerClient client) {
            // Clean up all zone tracker states to prevent memory leaks
            PvPZoneTracker.cleanupPlayerState(client);
            ProtectedZoneTracker.cleanupPlayer(client);
            SettlementProtectionTracker.cleanupPlayer(client);
        }
    }
}


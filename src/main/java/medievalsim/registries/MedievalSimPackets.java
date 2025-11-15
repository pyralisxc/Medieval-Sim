package medievalsim.registries;

import java.util.Arrays;
import java.util.List;

import medievalsim.util.ModLogger;

import medievalsim.packets.PacketConfigurePvPZone;
import medievalsim.packets.PacketConfigureProtectedZone;
import medievalsim.packets.PacketCreateZone;
import medievalsim.packets.PacketDeleteZone;
import medievalsim.packets.PacketExpandZone;
import medievalsim.packets.PacketPvPZoneEntryDialog;
import medievalsim.packets.PacketPvPZoneEntryResponse;
import medievalsim.packets.PacketPvPZoneExitDialog;
import medievalsim.packets.PacketPvPZoneExitResponse;
import medievalsim.packets.PacketPvPZoneSpawnDialog;
import medievalsim.packets.PacketPvPZoneSpawnResponse;
import medievalsim.packets.PacketRenameZone;
import medievalsim.packets.PacketRequestPlayerList;
import medievalsim.packets.PacketRequestZoneSync;
import medievalsim.packets.PacketShrinkZone;
import medievalsim.packets.PacketZoneChanged;
import medievalsim.packets.PacketZoneRemoved;
import medievalsim.packets.PacketZoneSync;
import medievalsim.packets.PacketForceClean;
import medievalsim.packets.PacketExecuteCommand;
import medievalsim.packets.PacketCommandResult;
import medievalsim.packets.PacketSyncSettings;
import necesse.engine.network.Packet;
import necesse.engine.registries.PacketRegistry;

public class MedievalSimPackets {
    
    // Centralized packet list for automatic counting
    private static final List<Class<? extends Packet>> PACKETS = Arrays.asList(
        // Zone management packets
        PacketCreateZone.class,
        PacketExpandZone.class,
        PacketShrinkZone.class,
        PacketDeleteZone.class,
        PacketRenameZone.class,
        PacketConfigurePvPZone.class,
        PacketConfigureProtectedZone.class,  // NEW: Protected zone configuration
        
        // Zone synchronization packets
        PacketZoneSync.class,
        PacketRequestZoneSync.class,
        PacketZoneChanged.class,
        PacketZoneRemoved.class,
        
        // PvP zone interaction packets
        PacketPvPZoneEntryDialog.class,
        PacketPvPZoneEntryResponse.class,
        PacketPvPZoneExitDialog.class,
        PacketPvPZoneExitResponse.class,
        PacketPvPZoneSpawnDialog.class,
        PacketPvPZoneSpawnResponse.class,
        
        // Admin tools packets
        PacketForceClean.class,
        PacketRequestPlayerList.class,
        
        // Command Center packets
        PacketExecuteCommand.class,
        PacketCommandResult.class,
        PacketSyncSettings.class
    );
    
    public static void registerCore() {
        PACKETS.forEach(PacketRegistry::registerPacket);
        ModLogger.info("Registered %d network packets", PACKETS.size());
    }
}


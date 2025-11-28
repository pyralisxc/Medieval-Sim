package medievalsim.packets.registry;

import java.util.List;

import medievalsim.packets.PacketConfigureProtectedZone;
import medievalsim.packets.PacketConfigurePvPZone;
import medievalsim.packets.PacketConfigureSettlementProtection;
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
import medievalsim.packets.PacketRequestZoneSync;
import medievalsim.packets.PacketShrinkZone;
import medievalsim.packets.PacketZoneChanged;
import medievalsim.packets.PacketZoneRemoved;
import medievalsim.packets.PacketZoneSync;
import medievalsim.packets.core.PacketRegistrar;
import medievalsim.packets.core.PacketSpec;

public final class ZonePacketRegistrar {
    private static final PacketRegistrar REGISTRAR = () -> List.of(
        new PacketSpec(PacketCreateZone.class, "zones", "Create new admin zone"),
        new PacketSpec(PacketExpandZone.class, "zones", "Expand zone"),
        new PacketSpec(PacketShrinkZone.class, "zones", "Shrink zone"),
        new PacketSpec(PacketDeleteZone.class, "zones", "Delete zone"),
        new PacketSpec(PacketRenameZone.class, "zones", "Rename zone"),
        new PacketSpec(PacketConfigurePvPZone.class, "zones", "Configure PvP zone"),
        new PacketSpec(PacketConfigureProtectedZone.class, "zones", "Configure protected zone"),
        new PacketSpec(PacketConfigureSettlementProtection.class, "zones", "Configure settlement protection"),
        new PacketSpec(PacketZoneSync.class, "zones", "Full zone sync"),
        new PacketSpec(PacketRequestZoneSync.class, "zones", "Client -> server sync request"),
        new PacketSpec(PacketZoneChanged.class, "zones", "Zone changed broadcast"),
        new PacketSpec(PacketZoneRemoved.class, "zones", "Zone removed broadcast"),
        new PacketSpec(PacketPvPZoneEntryDialog.class, "zones", "Prompt entry dialog"),
        new PacketSpec(PacketPvPZoneEntryResponse.class, "zones", "Client -> server entry response"),
        new PacketSpec(PacketPvPZoneExitDialog.class, "zones", "Prompt exit dialog"),
        new PacketSpec(PacketPvPZoneExitResponse.class, "zones", "Client -> server exit response"),
        new PacketSpec(PacketPvPZoneSpawnDialog.class, "zones", "Prompt spawn dialog"),
        new PacketSpec(PacketPvPZoneSpawnResponse.class, "zones", "Client -> server spawn response")
    );

    private ZonePacketRegistrar() {
    }

    public static List<PacketSpec> getSpecs() {
        return REGISTRAR.getSpecs();
    }
}

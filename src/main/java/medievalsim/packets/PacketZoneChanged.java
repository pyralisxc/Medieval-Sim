package medievalsim.packets;
import medievalsim.ui.AdminToolsHudForm;
import medievalsim.ui.AdminToolsHudManager;
import medievalsim.zones.domain.AdminZone;
import medievalsim.zones.domain.AdminZonesLevelData;
import medievalsim.zones.domain.GuildZone;
import medievalsim.zones.domain.ProtectedZone;
import medievalsim.zones.domain.PvPZone;
import medievalsim.zones.domain.ZoneType;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.Zoning;
import necesse.level.maps.Level;

public class PacketZoneChanged
extends Packet {
    private final int uniqueID;
    private final ZoneType zoneType;
    private final String name;
    private final long creatorAuth;
    private final int colorHue;
    private final byte[] zoningData;
    // PvP-specific fields
    private final float damageMultiplier;
    private final int combatLockSeconds;
    // Guild-specific fields
    private final int guildID;
    private final String guildName;
    private final boolean isPurchasable;
    private final long purchaseCost;

    public PacketZoneChanged(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.uniqueID = reader.getNextInt();
        this.zoneType = ZoneType.fromId(reader.getNextInt());
        this.name = reader.getNextString();
        this.creatorAuth = reader.getNextLong();
        this.colorHue = reader.getNextInt();
        int zoningDataLength = reader.getNextInt();
        this.zoningData = reader.getNextBytes(zoningDataLength);
        
        // Read type-specific data
        if (this.zoneType == ZoneType.PVP) {
            this.damageMultiplier = reader.getNextFloat();
            this.combatLockSeconds = reader.getNextInt();
            this.guildID = -1;
            this.guildName = "";
            this.isPurchasable = false;
            this.purchaseCost = 0;
        } else if (this.zoneType == ZoneType.GUILD) {
            this.damageMultiplier = medievalsim.config.ModConfig.Zones.defaultDamageMultiplier;
            this.combatLockSeconds = medievalsim.config.ModConfig.Zones.defaultCombatLockSeconds;
            this.guildID = reader.getNextInt();
            this.guildName = reader.getNextString();
            this.isPurchasable = reader.getNextBoolean();
            this.purchaseCost = reader.getNextLong();
        } else {
            // Protected zone - use defaults
            this.damageMultiplier = medievalsim.config.ModConfig.Zones.defaultDamageMultiplier;
            this.combatLockSeconds = medievalsim.config.ModConfig.Zones.defaultCombatLockSeconds;
            this.guildID = -1;
            this.guildName = "";
            this.isPurchasable = false;
            this.purchaseCost = 0;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public PacketZoneChanged(AdminZone zone) {
        this.uniqueID = zone.uniqueID;
        this.zoneType = ZoneType.fromZone(zone);
        this.name = zone.name;
        this.creatorAuth = zone.creatorAuth;
        this.colorHue = zone.colorHue;
        
        // Set type-specific fields
        if (zone instanceof PvPZone) {
            PvPZone pvpZone = (PvPZone)zone;
            this.damageMultiplier = pvpZone.damageMultiplier;
            this.combatLockSeconds = pvpZone.combatLockSeconds;
            this.guildID = -1;
            this.guildName = "";
            this.isPurchasable = false;
            this.purchaseCost = 0;
        } else if (zone instanceof GuildZone) {
            GuildZone guildZone = (GuildZone)zone;
            this.damageMultiplier = medievalsim.config.ModConfig.Zones.defaultDamageMultiplier;
            this.combatLockSeconds = medievalsim.config.ModConfig.Zones.defaultCombatLockSeconds;
            this.guildID = guildZone.getGuildID();
            this.guildName = guildZone.getGuildName();
            this.isPurchasable = guildZone.isPurchasable();
            this.purchaseCost = guildZone.getPurchaseCost();
        } else {
            this.damageMultiplier = medievalsim.config.ModConfig.Zones.defaultDamageMultiplier;
            this.combatLockSeconds = medievalsim.config.ModConfig.Zones.defaultCombatLockSeconds;
            this.guildID = -1;
            this.guildName = "";
            this.isPurchasable = false;
            this.purchaseCost = 0;
        }
        
        Packet zoningPacket = new Packet();
        PacketWriter zoningWriter = new PacketWriter(zoningPacket);
        Zoning zoning = zone.zoning;
        synchronized (zoning) {
            zone.zoning.writeZonePacket(zoningWriter);
        }
        this.zoningData = zoningPacket.getBytes(0, zoningPacket.getSize());
        
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(this.uniqueID);
        writer.putNextInt(this.zoneType.getId());
        writer.putNextString(this.name);
        writer.putNextLong(this.creatorAuth);
        writer.putNextInt(this.colorHue);
        writer.putNextInt(this.zoningData.length);
        writer.putNextBytes(this.zoningData);
        
        // Write type-specific data
        if (this.zoneType == ZoneType.PVP) {
            writer.putNextFloat(this.damageMultiplier);
            writer.putNextInt(this.combatLockSeconds);
        } else if (this.zoneType == ZoneType.GUILD) {
            writer.putNextInt(this.guildID);
            writer.putNextString(this.guildName);
            writer.putNextBoolean(this.isPurchasable);
            writer.putNextLong(this.purchaseCost);
        }
    }

    /**
     * Legacy constructor for backward compatibility.
     * @deprecated Use {@link #PacketZoneChanged(AdminZone)} instead
     */
    @Deprecated
    public PacketZoneChanged(AdminZone zone, boolean isProtectedZone) {
        this(zone);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        Level level = client.getLevel();
        if (level == null) {
            return;
        }
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, true);
        if (zoneData == null) {
            return;
        }
        
        AdminZone zone;
        switch (this.zoneType) {
            case PVP:
                zone = new PvPZone(this.uniqueID, this.name, this.creatorAuth, this.colorHue, this.damageMultiplier, this.combatLockSeconds);
                break;
            case GUILD:
                GuildZone guildZone = new GuildZone(this.uniqueID, this.name, this.creatorAuth, this.colorHue);
                guildZone.setGuildID(this.guildID);
                guildZone.setGuildName(this.guildName);
                guildZone.setPurchasable(this.isPurchasable, this.purchaseCost);
                zone = guildZone;
                break;
            default:
                zone = new ProtectedZone(this.uniqueID, this.name, this.creatorAuth, this.colorHue);
                break;
        }
        
        Packet tempPacket = new Packet(this.zoningData);
        PacketReader zoningReader = new PacketReader(tempPacket);
        Zoning zoning = zone.zoning;
        synchronized (zoning) {
            zone.zoning.readZonePacket(zoningReader);
        }
        
        // Store in appropriate repository
        switch (this.zoneType) {
            case PVP:
                zoneData.putPvPZone((PvPZone)zone);
                break;
            case GUILD:
                zoneData.putGuildZone((GuildZone)zone);
                break;
            default:
                zoneData.putProtectedZone((ProtectedZone)zone);
                break;
        }
        
        AdminToolsHudForm hudForm = AdminToolsHudManager.getHudForm();
        if (hudForm != null) {
            hudForm.onZoneChanged(zone, this.zoneType);
        }
    }
}


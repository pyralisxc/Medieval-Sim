/*
 * Guild Teleport Banner Object Entity for Medieval Sim Mod
 * Stores guild ownership and teleport network data.
 */
package medievalsim.guilds.objects;

import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.maps.Level;

/**
 * Object entity for guild teleport banners.
 * Stores which guild owns this banner for the teleport network.
 */
public class GuildTeleportBannerObjectEntity extends ObjectEntity {

    private int ownerGuildID = -1;
    private String customName = null; // Optional name like "Main Hall", "Mine Entrance"

    public GuildTeleportBannerObjectEntity(Level level, int x, int y) {
        super(level, "guildteleportbanner", x, y);
    }

    /**
     * Set the owning guild.
     */
    public void setOwnerGuildID(int guildID) {
        this.ownerGuildID = guildID;
    }

    /**
     * Get the owning guild ID.
     */
    public int getOwnerGuildID() {
        return ownerGuildID;
    }

    /**
     * Check if this banner belongs to a specific guild.
     */
    public boolean belongsToGuild(int guildID) {
        return this.ownerGuildID == guildID;
    }

    /**
     * Set a custom display name for this teleport location.
     */
    public void setCustomName(String name) {
        this.customName = name;
    }

    /**
     * Get the display name for this teleport location.
     */
    public String getDisplayName() {
        if (customName != null && !customName.isEmpty()) {
            return customName;
        }
        // Default to position-based name
        return String.format("Banner (%d, %d)", tileX, tileY);
    }

    /**
     * Get the full display name including guild name if available.
     */
    public String getFullDisplayName(GuildManager gm) {
        if (gm != null && ownerGuildID > 0) {
            GuildData guild = gm.getGuild(ownerGuildID);
            if (guild != null) {
                return guild.getName() + " - " + getDisplayName();
            }
        }
        return getDisplayName();
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        save.addInt("ownerGuildID", ownerGuildID);
        if (customName != null) {
            save.addSafeString("customName", customName);
        }
    }

    @Override
    public void applyLoadData(LoadData load) {
        super.applyLoadData(load);
        this.ownerGuildID = load.getInt("ownerGuildID", -1);
        this.customName = load.getSafeString("customName", null);
    }

    @Override
    public void setupContentPacket(PacketWriter writer) {
        super.setupContentPacket(writer);
        writer.putNextInt(ownerGuildID);
        writer.putNextBoolean(customName != null);
        if (customName != null) {
            writer.putNextString(customName);
        }
    }

    @Override
    public void applyContentPacket(PacketReader reader) {
        super.applyContentPacket(reader);
        this.ownerGuildID = reader.getNextInt();
        boolean hasName = reader.getNextBoolean();
        if (hasName) {
            this.customName = reader.getNextString();
        }
    }
}

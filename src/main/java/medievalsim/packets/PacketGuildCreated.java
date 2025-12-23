package medievalsim.packets;

import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.client.Client;
import medievalsim.guilds.GuildRank;
import medievalsim.guilds.client.ClientGuildManager;
import medievalsim.util.ModLogger;

/**
 * Server -> Client: Notify about created guild.
 * - Sent to the creator to confirm their new guild and update client state
 * - Also broadcast to all others as an announcement (they just see chat message)
 */
public class PacketGuildCreated extends Packet {
    public int guildID;
    public String guildName;
    public boolean isCreator; // True if recipient is the guild creator

    public PacketGuildCreated(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.guildName = reader.getNextString();
        this.isCreator = reader.getNextBoolean();
    }

    public PacketGuildCreated(int guildID, String guildName, boolean isCreator) {
        this.guildID = guildID;
        this.guildName = guildName;
        this.isCreator = isCreator;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextString(guildName);
        writer.putNextBoolean(isCreator);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        try {
            if (isCreator) {
                // This is the creator - update their guild state
                ClientGuildManager.get().setCurrentGuild(guildID, guildName, GuildRank.LEADER);
                client.chat.addMessage("\u00a7aYou have created the guild \u00a76" + guildName + "\u00a7a!");
                ModLogger.info("Created guild %d (%s) - local player is leader", guildID, guildName);
            } else {
                // Announcement to others
                client.chat.addMessage("\u00a77A new guild has been formed: \u00a76" + guildName);
                ModLogger.debug("Guild %d (%s) created by another player", guildID, guildName);
            }
        } catch (Exception e) {
            ModLogger.error("PacketGuildCreated: client processing failed", e);
        }
    }
}

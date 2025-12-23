package medievalsim.packets;

import medievalsim.guilds.GuildRank;
import medievalsim.guilds.client.ClientGuildManager;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;

/**
 * Server -> Client: Confirmation that the local player joined a guild.
 * Sent to the player who just joined (via invite acceptance or public join).
 */
public class PacketGuildJoined extends Packet {
    public int guildID;
    public String guildName;
    public GuildRank rank;

    public PacketGuildJoined(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.guildName = reader.getNextString();
        this.rank = GuildRank.fromLevel(reader.getNextInt());
    }

    public PacketGuildJoined(int guildID, String guildName, GuildRank rank) {
        this.guildID = guildID;
        this.guildName = guildName;
        this.rank = rank;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextString(guildName);
        writer.putNextInt(rank.level);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        try {
            // Update ClientGuildManager with our new guild
            ClientGuildManager.get().setCurrentGuild(guildID, guildName, rank);
            ClientGuildManager.get().clearPendingInvite(); // Clear any pending invite
            
            // Show notification
            client.chat.addMessage("\u00a7aYou have joined \u00a76" + guildName + "\u00a7a as " + rank.getDisplayName() + "!");
            
            ModLogger.info("Joined guild %d (%s) as %s", guildID, guildName, rank.getDisplayName());
        } catch (Exception e) {
            ModLogger.error("PacketGuildJoined: client processing failed", e);
        }
    }
}

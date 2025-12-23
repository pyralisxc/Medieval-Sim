package medievalsim.packets;

import medievalsim.guilds.GuildRank;
import medievalsim.guilds.client.ClientGuildManager;
import medievalsim.guilds.client.ClientGuildManager.GuildMemberUpdateType;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;

/**
 * Server -> Client: Notification that a new member joined the guild.
 * Sent to existing guild members when someone joins.
 */
public class PacketGuildMemberJoined extends Packet {
    public int guildID;
    public long memberAuth;
    public String memberName;
    public GuildRank rank;

    public PacketGuildMemberJoined(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.memberAuth = reader.getNextLong();
        this.memberName = reader.getNextString();
        this.rank = GuildRank.fromLevel(reader.getNextInt());
    }

    public PacketGuildMemberJoined(int guildID, long memberAuth, String memberName, GuildRank rank) {
        this.guildID = guildID;
        this.memberAuth = memberAuth;
        this.memberName = memberName != null ? memberName : "Unknown";
        this.rank = rank;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextLong(memberAuth);
        writer.putNextString(this.memberName);
        writer.putNextInt(rank.level);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        try {
            // Notify ClientGuildManager for UI refresh
            ClientGuildManager.get().notifyMemberUpdate(guildID, memberAuth, GuildMemberUpdateType.JOINED);
            
            // Show chat notification
            client.chat.addMessage("\u00a7a" + memberName + " has joined the guild!");
            
            ModLogger.debug("Guild member %s joined guild %d as %s", 
                memberName, guildID, rank.getDisplayName());
        } catch (Exception e) {
            ModLogger.error("PacketGuildMemberJoined: client processing failed", e);
        }
    }
}

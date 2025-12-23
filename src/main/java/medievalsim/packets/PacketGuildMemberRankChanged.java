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
import necesse.gfx.GameColor;

/**
 * Server -> Client: Notification that a guild member's rank changed.
 */
public class PacketGuildMemberRankChanged extends Packet {
    public int guildID;
    public long memberAuth;
    public String memberName;
    public GuildRank newRank;

    public PacketGuildMemberRankChanged(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.memberAuth = reader.getNextLong();
        this.memberName = reader.getNextString();
        this.newRank = GuildRank.fromLevel(reader.getNextInt());
    }

    public PacketGuildMemberRankChanged(int guildID, long memberAuth, String memberName, GuildRank newRank) {
        this.guildID = guildID;
        this.memberAuth = memberAuth;
        this.memberName = memberName != null ? memberName : "Unknown";
        this.newRank = newRank;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextLong(memberAuth);
        writer.putNextString(this.memberName);
        writer.putNextInt(newRank.level);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        try {
            ClientGuildManager cgm = ClientGuildManager.get();
            
            // Check if this is about the local player
            if (client.getPlayer() != null && client.getPlayer().getUniqueID() == memberAuth) {
                // Our rank changed
                cgm.updateRank(guildID, newRank);
                client.chat.addMessage(GameColor.GREEN.getColorCode() + "Your guild rank has changed to " + GameColor.YELLOW.getColorCode() + newRank.getDisplayName());
            } else {
                // Another member's rank changed - notify for UI refresh
                cgm.notifyMemberUpdate(guildID, memberAuth, GuildMemberUpdateType.RANK_CHANGED);
                client.chat.addMessage(GameColor.GRAY.getColorCode() + memberName + " is now " + newRank.getDisplayName());
            }
            
            ModLogger.debug("Guild member %s rank changed to %s in guild %d",
                memberName, newRank.getDisplayName(), guildID);
        } catch (Exception e) {
            ModLogger.error("PacketGuildMemberRankChanged: client processing failed", e);
        }
    }
}

package medievalsim.packets;

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
 * Server -> Client: Notification that a member left (or was kicked).
 * Sent to remaining guild members when someone leaves/is kicked.
 */
public class PacketGuildMemberLeft extends Packet {
    public int guildID;
    public long memberAuth;
    public String memberName;
    public boolean wasKicked;

    public PacketGuildMemberLeft(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.memberAuth = reader.getNextLong();
        this.memberName = reader.getNextString();
        this.wasKicked = reader.getNextBoolean();
    }

    public PacketGuildMemberLeft(int guildID, long memberAuth, String memberName, boolean wasKicked) {
        this.guildID = guildID;
        this.memberAuth = memberAuth;
        this.memberName = memberName != null ? memberName : "Someone";
        this.wasKicked = wasKicked;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextLong(memberAuth);
        writer.putNextString(this.memberName);
        writer.putNextBoolean(wasKicked);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        try {
            // Notify ClientGuildManager for UI refresh
            GuildMemberUpdateType updateType = wasKicked ? GuildMemberUpdateType.KICKED : GuildMemberUpdateType.LEFT;
            ClientGuildManager.get().notifyMemberUpdate(guildID, memberAuth, updateType);
            
            // Show chat notification
            String action = wasKicked ? "was kicked from" : "has left";
            client.chat.addMessage(GameColor.GRAY.getColorCode() + memberName + " " + action + " the guild.");
            
            ModLogger.debug("Guild member %s %s guild %d", memberName, action, guildID);
        } catch (Exception e) {
            ModLogger.error("PacketGuildMemberLeft: client processing failed", e);
        }
    }
}

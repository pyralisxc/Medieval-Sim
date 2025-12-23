package medievalsim.packets;

import medievalsim.guilds.client.ClientGuildManager;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.gfx.GameColor;

/**
 * Server -> Client: Notification that the player was kicked from a guild.
 * Sent directly to the kicked player.
 */
public class PacketGuildKicked extends Packet {
    public int guildID;
    public String guildName;
    public String reason;

    public PacketGuildKicked(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.guildName = reader.getNextString();
        this.reason = reader.getNextString();
    }

    public PacketGuildKicked(int guildID, String guildName, String reason) {
        this.guildID = guildID;
        this.guildName = guildName;
        this.reason = reason != null ? reason : "";
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextString(guildName);
        writer.putNextString(this.reason);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        try {
            // Update ClientGuildManager - we're no longer in a guild
            ClientGuildManager.get().clearCurrentGuild(guildID, guildName, true, reason);
            
            // Show notification
            String message = GameColor.RED.getColorCode() + "You have been kicked from " + GameColor.YELLOW.getColorCode() + guildName;
            if (!reason.isEmpty()) {
                message += GameColor.RED.getColorCode() + ". Reason: " + reason;
            }
            client.chat.addMessage(message);
            
            ModLogger.info("Kicked from guild %d (%s)", guildID, guildName);
        } catch (Exception e) {
            ModLogger.error("PacketGuildKicked: client processing failed", e);
        }
    }
}

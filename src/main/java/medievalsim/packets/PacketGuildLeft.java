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
 * Server -> Client: Confirmation that the player left a guild.
 * Sent to the player who left voluntarily.
 */
public class PacketGuildLeft extends Packet {
    public int guildID;
    public String guildName;

    public PacketGuildLeft(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.guildName = reader.getNextString();
    }

    public PacketGuildLeft(int guildID, String guildName) {
        this.guildID = guildID;
        this.guildName = guildName;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextString(guildName);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        try {
            // Update ClientGuildManager - we're no longer in a guild
            ClientGuildManager.get().clearCurrentGuild(guildID, guildName, false, "");
            
            // Show notification
            client.chat.addMessage(GameColor.GREEN.getColorCode() + "You have left " + GameColor.YELLOW.getColorCode() + guildName);
            
            ModLogger.info("Left guild %d (%s)", guildID, guildName);
        } catch (Exception e) {
            ModLogger.error("PacketGuildLeft: client processing failed", e);
        }
    }
}

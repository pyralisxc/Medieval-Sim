package medievalsim.packets;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;

/**
 * Server -> Client: Notification that a guild was disbanded.
 */
public class PacketGuildDisbanded extends Packet {
    public int guildID;
    public String guildName;
    public long goldReceived;

    public PacketGuildDisbanded(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.guildName = reader.getNextString();
        this.goldReceived = reader.getNextLong();
    }

    public PacketGuildDisbanded(int guildID, String guildName, long goldReceived) {
        this.guildID = guildID;
        this.guildName = guildName;
        this.goldReceived = goldReceived;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextString(guildName);
        writer.putNextLong(goldReceived);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        // Client-side: Show notification and update local guild state
        String message = guildName + " has been disbanded.";
        if (goldReceived > 0) {
            message += " You received " + goldReceived + " gold from the treasury.";
        }
        client.chat.addMessage(message);
        medievalsim.util.ModLogger.info("Guild %d (%s) disbanded. Received %d gold.",
            guildID, guildName, goldReceived);
    }
}

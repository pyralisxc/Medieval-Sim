package medievalsim.packets;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;

/**
 * Server -> Client: Notification that guild leadership was transferred.
 */
public class PacketGuildLeadershipTransferred extends Packet {
    public int guildID;
    public long oldLeaderAuth;
    public long newLeaderAuth;

    public PacketGuildLeadershipTransferred(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.oldLeaderAuth = reader.getNextLong();
        this.newLeaderAuth = reader.getNextLong();
    }

    public PacketGuildLeadershipTransferred(int guildID, long oldLeaderAuth, long newLeaderAuth) {
        this.guildID = guildID;
        this.oldLeaderAuth = oldLeaderAuth;
        this.newLeaderAuth = newLeaderAuth;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextLong(oldLeaderAuth);
        writer.putNextLong(newLeaderAuth);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        // Client-side: Update local guild state with new leader
        medievalsim.util.ModLogger.debug("Guild %d leadership transferred from %d to %d",
            guildID, oldLeaderAuth, newLeaderAuth);
    }
}

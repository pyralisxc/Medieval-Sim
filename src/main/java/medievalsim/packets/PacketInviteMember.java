package medievalsim.packets;

import medievalsim.util.ModLogger;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.GuildData;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Client -> Server: Invite a player to a guild
 */
public class PacketInviteMember extends Packet {
    public int guildID;
    public long targetAuth;

    public PacketInviteMember(byte[] data) {
        super(data);
        PacketReader r = new PacketReader(this);
        this.guildID = r.getNextInt();
        this.targetAuth = r.getNextLong();
    }

    public PacketInviteMember(int guildID, long targetAuth) {
        this.guildID = guildID;
        this.targetAuth = targetAuth;
        PacketWriter w = new PacketWriter(this);
        w.putNextInt(guildID);
        w.putNextLong(targetAuth);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            if (client == null || client.getServer() == null) return;
            GuildManager gm = medievalsim.guilds.GuildManager.get(client.getServer().world);
            if (gm == null) {
                client.sendChatMessage("Guild system not available on server.");
                return;
            }

            boolean ok = gm.sendInvitation(guildID, targetAuth, client.authentication);
            if (!ok) {
                client.sendChatMessage("Failed to send guild invite. Check permissions or target status.");
                return;
            }

            // Notify target if online
            ServerClient targetClient = client.getServer().getClientByAuth(targetAuth);
            if (targetClient != null) {
                GuildData gd = gm.getGuild(guildID);
                String guildName = gd != null ? gd.getName() : Integer.toString(guildID);
                targetClient.sendPacket(new PacketGuildInvited(guildID, guildName, client.authentication, client.getName()));
            }

            client.sendChatMessage("Guild invite sent.");
            ModLogger.info("%s invited auth=%d to guild %d", client.getName(), targetAuth, guildID);
        } catch (Exception e) {
            ModLogger.error("Exception in PacketInviteMember.processServer", e);
            client.sendChatMessage("An unexpected error occurred while sending guild invite.");
        }
    }
}

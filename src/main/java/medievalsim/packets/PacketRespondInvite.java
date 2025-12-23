package medievalsim.packets;

import medievalsim.util.ModLogger;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildRank;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Client -> Server: Respond to a guild invite (accept or decline)
 */
public class PacketRespondInvite extends Packet {
    public int guildID;
    public boolean accept;

    public PacketRespondInvite(byte[] data) {
        super(data);
        PacketReader r = new PacketReader(this);
        this.guildID = r.getNextInt();
        this.accept = r.getNextBoolean();
    }

    public PacketRespondInvite(int guildID, boolean accept) {
        this.guildID = guildID;
        this.accept = accept;
        PacketWriter w = new PacketWriter(this);
        w.putNextInt(guildID);
        w.putNextBoolean(accept);
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

            GuildData gd = gm.getGuild(guildID);
            if (gd == null) {
                client.sendChatMessage("Guild no longer exists.");
                return;
            }

            if (accept) {
                boolean ok = gm.acceptInvitation(guildID, client.authentication);
                if (!ok) {
                    client.sendChatMessage("Failed to accept guild invitation.");
                    return;
                }
                
                String guildName = gd.getName();
                String playerName = client.getName();
                GuildRank rank = gd.getMemberRank(client.authentication);
                if (rank == null) rank = GuildRank.RECRUIT;
                
                // Notify the joining player with proper packet
                client.sendPacket(new PacketGuildJoined(guildID, guildName, rank));
                
                // Notify existing guild members
                broadcastToGuildMembersExcept(server, gm, guildID, client,
                    new PacketGuildMemberJoined(guildID, client.authentication, playerName, rank));
                
                ModLogger.info("%s accepted invite and joined guild %d (%s)", playerName, guildID, guildName);
            } else {
                gm.declineInvitation(guildID, client.authentication);
                client.sendChatMessage("Guild invitation declined.");
                ModLogger.debug("%s declined invite to guild %d", client.getName(), guildID);
            }
        } catch (Exception e) {
            ModLogger.error("Exception in PacketRespondInvite.processServer", e);
            client.sendChatMessage("An unexpected error occurred while responding to guild invite.");
        }
    }
    
    private void broadcastToGuildMembersExcept(Server server, GuildManager gm, int guildID, 
                                                ServerClient except, Packet packet) {
        GuildData guild = gm.getGuild(guildID);
        if (guild == null) return;
        for (Long memberAuth : guild.getMembers().keySet()) {
            if (except != null && memberAuth == except.authentication) continue;
            ServerClient memberClient = server.getClientByAuth(memberAuth);
            if (memberClient != null) {
                memberClient.sendPacket(packet);
            }
        }
    }
}

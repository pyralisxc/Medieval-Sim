package medievalsim.packets;

import medievalsim.guilds.GuildManager;
import medievalsim.guilds.GuildRank;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Client -> Server: Request to transfer guild leadership.
 */
public class PacketTransferLeadership extends Packet {
    public int guildID;
    public long newLeaderAuth;

    public PacketTransferLeadership(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.newLeaderAuth = reader.getNextLong();
    }

    public PacketTransferLeadership(int guildID, long newLeaderAuth) {
        this.guildID = guildID;
        this.newLeaderAuth = newLeaderAuth;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextLong(newLeaderAuth);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            if (client == null || client.getServer() == null) return;

            GuildManager gm = GuildManager.get(client.getServer().world);
            if (gm == null) {
                client.sendChatMessage("Guild system not available.");
                return;
            }

            var guild = gm.getGuild(guildID);
            if (guild == null) {
                client.sendChatMessage("Guild not found.");
                return;
            }

            // Check if current leader
            if (guild.getLeaderAuth() != client.authentication) {
                client.sendChatMessage("Only the current leader can transfer leadership.");
                return;
            }

            // Can't transfer to yourself
            if (newLeaderAuth == client.authentication) {
                client.sendChatMessage("You are already the leader.");
                return;
            }

            // Check new leader is member
            if (!guild.isMember(newLeaderAuth)) {
                client.sendChatMessage("Target must be a guild member to become leader.");
                return;
            }

            boolean success = gm.transferLeadership(guildID, newLeaderAuth, client.authentication);
            
            if (success) {
                // Get names for notifications
                ServerClient newLeaderClient = server.getClientByAuth(newLeaderAuth);
                String newLeaderName = newLeaderClient != null ? newLeaderClient.getName() : "Unknown";
                String oldLeaderName = client.getName();
                
                // Notify all guild members
                broadcastToGuildMembers(server, gm, guildID,
                    new PacketGuildLeadershipTransferred(guildID, client.authentication, newLeaderAuth));
                
                // Also send rank change for both players
                broadcastToGuildMembers(server, gm, guildID,
                    new PacketGuildMemberRankChanged(guildID, newLeaderAuth, newLeaderName, GuildRank.LEADER));
                broadcastToGuildMembers(server, gm, guildID,
                    new PacketGuildMemberRankChanged(guildID, client.authentication, oldLeaderName, GuildRank.OFFICER));
                    
                ModLogger.info("Leadership of guild %d transferred from %s to %s",
                    guildID, oldLeaderName, newLeaderName);
            } else {
                client.sendChatMessage("Failed to transfer leadership. The new leader may already lead another guild.");
            }
        } catch (Exception e) {
            client.sendChatMessage("An error occurred while transferring leadership.");
            ModLogger.error("Exception in PacketTransferLeadership.processServer", e);
        }
    }

    private void broadcastToGuildMembers(Server server, GuildManager gm, int guildID, Packet packet) {
        var guild = gm.getGuild(guildID);
        if (guild == null) return;
        for (Long memberAuth : guild.getMembers().keySet()) {
            ServerClient memberClient = server.getClientByAuth(memberAuth);
            if (memberClient != null) {
                memberClient.sendPacket(packet);
            }
        }
    }
}

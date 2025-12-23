package medievalsim.packets;

import medievalsim.guilds.GuildManager;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Client -> Server: Request to leave a guild voluntarily.
 */
public class PacketLeaveGuild extends Packet {
    public int guildID;

    public PacketLeaveGuild(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
    }

    public PacketLeaveGuild(int guildID) {
        this.guildID = guildID;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
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

            // Check if member
            if (!guild.isMember(client.authentication)) {
                client.sendChatMessage("You are not a member of this guild.");
                return;
            }

            // Check if leader trying to leave
            var rank = guild.getMemberRank(client.authentication);
            if (rank == medievalsim.guilds.GuildRank.LEADER) {
                if (guild.getMemberCount() > 1) {
                    client.sendChatMessage("As leader, you must transfer leadership before leaving, or disband the guild.");
                    return;
                }
                // Sole member - this will auto-disband
            }

            String guildName = guild.getName();
            String leaverName = client.getName();
            boolean success = gm.leaveGuild(guildID, client.authentication, false, 0);
            
            if (success) {
                // Notify the player who left
                client.sendPacket(new PacketGuildLeft(guildID, guildName));
                
                // Notify remaining guild members (if guild still exists)
                var remainingGuild = gm.getGuild(guildID);
                if (remainingGuild != null) {
                    broadcastToGuildMembers(server, gm, guildID,
                        new PacketGuildMemberLeft(guildID, client.authentication, leaverName, false));
                }
                
                ModLogger.info("Player %s left guild %d (%s)", leaverName, guildID, guildName);
            } else {
                client.sendChatMessage("Failed to leave guild.");
            }
        } catch (Exception e) {
            client.sendChatMessage("An error occurred while leaving guild.");
            ModLogger.error("Exception in PacketLeaveGuild.processServer", e);
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

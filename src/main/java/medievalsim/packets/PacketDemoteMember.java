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
 * Client -> Server: Request to demote a guild member.
 */
public class PacketDemoteMember extends Packet {
    public int guildID;
    public long targetAuth;

    public PacketDemoteMember(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.targetAuth = reader.getNextLong();
    }

    public PacketDemoteMember(int guildID, long targetAuth) {
        this.guildID = guildID;
        this.targetAuth = targetAuth;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextLong(targetAuth);
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

            GuildRank currentRank = guild.getMemberRank(targetAuth);
            if (currentRank == null) {
                client.sendChatMessage("Target is not a member of this guild.");
                return;
            }

            // Calculate new rank (one level down)
            GuildRank newRank = currentRank.getDemotedRank();
            if (newRank == null) {
                client.sendChatMessage("Cannot demote below Recruit rank.");
                return;
            }

            boolean success = gm.setMemberRank(guildID, targetAuth, newRank, client.authentication);
            if (success) {
                // Get target name for notification
                String targetName = "Unknown";
                ServerClient targetClient = server.getClientByAuth(targetAuth);
                if (targetClient != null) {
                    targetName = targetClient.getName();
                }
                
                // Notify all guild members
                broadcastToGuildMembers(server, gm, guildID,
                    new PacketGuildMemberRankChanged(guildID, targetAuth, targetName, newRank));
                ModLogger.info("Player %s demoted %s in guild %d to %s",
                    client.getName(), targetName, guildID, newRank.getDisplayName());
            } else {
                client.sendChatMessage("Failed to demote member. Check your permissions.");
            }
        } catch (Exception e) {
            client.sendChatMessage("An error occurred while demoting member.");
            ModLogger.error("Exception in PacketDemoteMember.processServer", e);
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

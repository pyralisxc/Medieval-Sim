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
 * Client -> Server: Request to kick a member from the guild.
 */
public class PacketKickMember extends Packet {
    public int guildID;
    public long targetAuth;
    public String reason;

    public PacketKickMember(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.targetAuth = reader.getNextLong();
        this.reason = reader.getNextString();
    }

    public PacketKickMember(int guildID, long targetAuth, String reason) {
        this.guildID = guildID;
        this.targetAuth = targetAuth;
        this.reason = reason != null ? reason : "";
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextLong(targetAuth);
        writer.putNextString(this.reason);
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

            // Can't kick yourself
            if (targetAuth == client.authentication) {
                client.sendChatMessage("You cannot kick yourself. Use leave guild instead.");
                return;
            }

            // Check target is actually a member
            if (!guild.isMember(targetAuth)) {
                client.sendChatMessage("Target is not a member of this guild.");
                return;
            }

            // Check permission to kick
            if (!guild.hasPermission(client.authentication, medievalsim.guilds.PermissionType.KICK_MEMBER)) {
                client.sendChatMessage("You don't have permission to kick members.");
                return;
            }

            // Can't kick someone of equal or higher rank
            var kickerRank = guild.getMemberRank(client.authentication);
            var targetRank = guild.getMemberRank(targetAuth);
            if (kickerRank == null || targetRank == null || targetRank.level >= kickerRank.level) {
                client.sendChatMessage("You cannot kick someone of equal or higher rank.");
                return;
            }

            // Notify the kicked player before removing them
            ServerClient targetClient = server.getClientByAuth(targetAuth);
            String guildName = guild.getName();
            String targetName = targetClient != null ? targetClient.getName() : "Unknown";
            
            boolean success = gm.leaveGuild(guildID, targetAuth, true, client.authentication);
            if (success) {
                // Notify the kicked player
                if (targetClient != null) {
                    targetClient.sendPacket(new PacketGuildKicked(guildID, guildName, reason));
                }
                
                // Notify remaining guild members
                broadcastToGuildMembers(server, gm, guildID,
                    new PacketGuildMemberLeft(guildID, targetAuth, targetName, true));
                    
                ModLogger.info("Player %s kicked %s from guild %d. Reason: %s",
                    client.getName(), targetName, guildID, reason);
            } else {
                client.sendChatMessage("Failed to kick member.");
            }
        } catch (Exception e) {
            client.sendChatMessage("An error occurred while kicking member.");
            ModLogger.error("Exception in PacketKickMember.processServer", e);
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

package medievalsim.packets;

import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.GuildRank;
import medievalsim.guilds.PlayerGuildData;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Client -> Server: Request to join a public guild directly (no invite needed).
 * Only works for guilds marked as public.
 */
public class PacketJoinPublicGuild extends Packet {
    public int guildID;

    public PacketJoinPublicGuild(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
    }

    public PacketJoinPublicGuild(int guildID) {
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

            // Get guild data
            GuildData guild = gm.getGuild(guildID);
            if (guild == null) {
                client.sendChatMessage("Guild not found.");
                return;
            }

            // Check if guild is public
            if (!guild.isPublic()) {
                client.sendChatMessage("This guild is private. You need an invitation to join.");
                return;
            }

            // Check if already in a guild (use PlayerGuildData)
            PlayerGuildData playerGD = gm.getPlayerData(client.authentication);
            if (playerGD != null && !playerGD.getMemberships().isEmpty()) {
                client.sendChatMessage("You are already in a guild. Leave your current guild first.");
                return;
            }

            // Check if already a member
            if (guild.isMember(client.authentication)) {
                client.sendChatMessage("You are already a member of this guild.");
                return;
            }

            // Check guild capacity (optional - depends on your GuildData implementation)
            // if (guild.getMemberCount() >= guild.getMaxMembers()) {
            //     client.sendChatMessage("This guild is full.");
            //     return;
            // }

            // Add member to guild using joinGuild
            boolean success = gm.joinGuild(guildID, client.authentication, GuildRank.RECRUIT);
            if (success) {
                String guildName = guild.getName();
                String playerName = client.getName();
                
                // Notify the joining player
                client.sendPacket(new PacketGuildJoined(guildID, guildName, GuildRank.RECRUIT));
                
                // Notify existing guild members
                broadcastToGuildMembersExcept(server, gm, guildID, client,
                    new PacketGuildMemberJoined(guildID, client.authentication, playerName, GuildRank.RECRUIT));
                
                ModLogger.info("Player %s joined public guild %d (%s)", playerName, guildID, guildName);
            } else {
                client.sendChatMessage("Failed to join guild. Please try again.");
            }
        } catch (Exception e) {
            client.sendChatMessage("An error occurred while joining guild.");
            ModLogger.error("Exception in PacketJoinPublicGuild.processServer", e);
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

package medievalsim.packets;

import medievalsim.guilds.GuildManager;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

import java.util.Map;

/**
 * Client -> Server: Request to disband a guild (leader only).
 */
public class PacketDisbandGuild extends Packet {
    public int guildID;
    public String confirmationName; // Must match guild name to confirm

    public PacketDisbandGuild(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.confirmationName = reader.getNextString();
    }

    public PacketDisbandGuild(int guildID, String confirmationName) {
        this.guildID = guildID;
        this.confirmationName = confirmationName;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextString(confirmationName);
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

            // Check if leader
            if (guild.getLeaderAuth() != client.authentication) {
                client.sendChatMessage("Only the guild leader can disband the guild.");
                return;
            }

            // Require name confirmation
            if (!guild.getName().equalsIgnoreCase(confirmationName)) {
                client.sendChatMessage("To disband, type the guild name exactly to confirm.");
                return;
            }

            String guildName = guild.getName();
            
            // Get all members before disbanding for notification
            var memberAuths = guild.getMembers().keySet().toArray(new Long[0]);
            
            // Disband and distribute treasury
            Map<Long, Long> distribution = gm.disbandGuild(guildID, client.authentication);
            
            if (distribution != null) {
                // Notify all former members
                for (Long memberAuth : memberAuths) {
                    ServerClient memberClient = server.getClientByAuth(memberAuth);
                    if (memberClient != null) {
                        long goldReceived = distribution.getOrDefault(memberAuth, 0L);
                        memberClient.sendPacket(new PacketGuildDisbanded(guildID, guildName, goldReceived));
                    }
                }
                
                ModLogger.info("Guild %d (%s) disbanded by %s. Treasury distributed to %d members.",
                    guildID, guildName, client.getName(), distribution.size());
            } else {
                client.sendChatMessage("Failed to disband guild.");
            }
        } catch (Exception e) {
            client.sendChatMessage("An error occurred while disbanding guild.");
            ModLogger.error("Exception in PacketDisbandGuild.processServer", e);
        }
    }
}

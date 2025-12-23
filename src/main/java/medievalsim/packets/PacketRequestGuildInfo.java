/*
 * PacketRequestGuildInfo - Client requests full guild info from server
 * Part of Medieval Sim Mod guild management system.
 */
package medievalsim.packets;

import medievalsim.guilds.GuildData;
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
 * Client->Server packet requesting full guild information.
 * Server responds with PacketGuildInfoResponse.
 */
public class PacketRequestGuildInfo extends Packet {

    public int guildID;

    public PacketRequestGuildInfo(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
    }

    public PacketRequestGuildInfo(int guildID) {
        this.guildID = guildID;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            GuildManager gm = GuildManager.get(client.getServer().world);
            if (gm == null) {
                ModLogger.warn("GuildManager null when processing guild info request");
                return;
            }

            GuildData guild = gm.getGuild(guildID);
            if (guild == null) {
                ModLogger.warn("Client %s requested info for non-existent guild %d", 
                    client.getName(), guildID);
                return;
            }

            // Verify requester is a member (only members can see full info)
            if (!guild.isMember(client.authentication)) {
                ModLogger.warn("Client %s is not member of guild %d, cannot view full info", 
                    client.getName(), guildID);
                return;
            }

            // Build member names list from online players
            var members = guild.getMembers();
            String[] memberNames = new String[members.size()];
            long[] memberAuths = new long[members.size()];
            int[] memberRanks = new int[members.size()];
            boolean[] memberOnline = new boolean[members.size()];

            int i = 0;
            for (var entry : members.entrySet()) {
                long auth = entry.getKey();
                GuildRank rank = entry.getValue();
                
                memberAuths[i] = auth;
                memberRanks[i] = rank.level;
                
                // Try to get player name from online clients
                ServerClient memberClient = server.getClientByAuth(auth);
                if (memberClient != null) {
                    memberNames[i] = memberClient.getName();
                    memberOnline[i] = true;
                } else {
                    // For offline players, we'd need a name cache
                    // For now, use auth as placeholder
                    memberNames[i] = "Player-" + auth;
                    memberOnline[i] = false;
                }
                i++;
            }

            // Get recent audit log entries (last 20)
            var auditLog = guild.getAuditLog();
            int auditCount = Math.min(auditLog.size(), 20);
            String[] auditEntries = new String[auditCount];
            long[] auditTimestamps = new long[auditCount];
            
            for (int j = 0; j < auditCount; j++) {
                var entry = auditLog.get(auditLog.size() - 1 - j); // Most recent first
                auditEntries[j] = entry.toString();
                auditTimestamps[j] = entry.getTimestamp();
            }

            // Send response
            client.sendPacket(new PacketGuildInfoResponse(
                guildID,
                guild.getName(),
                guild.getDescription(),
                guild.isPublic(),
                guild.getTreasury(),
                guild.getSymbolDesign(),
                memberAuths,
                memberNames,
                memberRanks,
                memberOnline,
                auditEntries,
                auditTimestamps,
                client.authentication // Include requester's auth for rank lookup
            ));

            ModLogger.debug("Sent guild info for guild %d to %s", guildID, client.getName());

        } catch (Exception e) {
            ModLogger.error("Error processing guild info request", e);
        }
    }
}

/*
 * PacketListGuilds - Client requests list of public guilds from server
 * Part of Medieval Sim Mod guild management system.
 */
package medievalsim.packets;

import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Client->Server packet requesting list of public guilds.
 * Server responds with PacketGuildsListResponse.
 */
public class PacketListGuilds extends Packet {

    public PacketListGuilds(byte[] data) {
        super(data);
        // No additional data needed
    }

    public PacketListGuilds() {
        // No parameters needed - just requesting the list
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            GuildManager gm = GuildManager.get(server.world);
            if (gm == null) {
                ModLogger.warn("GuildManager null when processing guild list request");
                return;
            }

            // Get all public guilds
            List<GuildData> publicGuilds = gm.getPublicGuilds();

            // Build arrays for response
            int count = publicGuilds.size();
            int[] guildIDs = new int[count];
            String[] guildNames = new String[count];
            int[] memberCounts = new int[count];
            long[] treasuries = new long[count];
            
            // Crest preview data
            int[] crestShapes = new int[count];
            int[] crestColors = new int[count];
            int[] crestEmblems = new int[count];

            for (int i = 0; i < count; i++) {
                GuildData g = publicGuilds.get(i);
                guildIDs[i] = g.getGuildID();
                guildNames[i] = g.getName();
                memberCounts[i] = g.getMemberCount();
                treasuries[i] = g.getTreasury();
                
                var symbol = g.getSymbolDesign();
                crestShapes[i] = symbol.getShape();
                crestColors[i] = symbol.getPrimaryColor();
                crestEmblems[i] = symbol.getEmblem();
            }

            // Get pending invitations for this player
            var pendingInvites = gm.getPendingInvitations(client.authentication);
            int[] inviteGuildIDs = new int[pendingInvites.size()];
            String[] inviteGuildNames = new String[pendingInvites.size()];
            
            int j = 0;
            for (int guildID : pendingInvites) {
                inviteGuildIDs[j] = guildID;
                GuildData g = gm.getGuild(guildID);
                inviteGuildNames[j] = (g != null) ? g.getName() : "Unknown Guild";
                j++;
            }

            // Send response
            client.sendPacket(new PacketGuildsListResponse(
                guildIDs, guildNames, memberCounts, treasuries,
                crestShapes, crestColors, crestEmblems,
                inviteGuildIDs, inviteGuildNames
            ));

            ModLogger.debug("Sent guild list (%d public, %d invites) to %s", 
                count, pendingInvites.size(), client.getName());

        } catch (Exception e) {
            ModLogger.error("Error processing guild list request", e);
        }
    }
}

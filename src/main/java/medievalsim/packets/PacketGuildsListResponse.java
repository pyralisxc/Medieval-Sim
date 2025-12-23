/*
 * PacketGuildsListResponse - Server sends list of public guilds to client
 * Part of Medieval Sim Mod guild management system.
 */
package medievalsim.packets;

import medievalsim.guilds.ui.GuildBrowserForm;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;

/**
 * Server->Client packet containing list of public guilds and pending invitations.
 * Used to populate the Guild Browser UI.
 */
public class PacketGuildsListResponse extends Packet {

    // Public guilds
    public int[] guildIDs;
    public String[] guildNames;
    public int[] memberCounts;
    public long[] treasuries;
    
    // Crest preview (simplified)
    public int[] crestShapes;
    public int[] crestColors;
    public int[] crestEmblems;
    
    // Pending invitations
    public int[] inviteGuildIDs;
    public String[] inviteGuildNames;

    public PacketGuildsListResponse(byte[] data) {
        super(data);
        PacketReader r = new PacketReader(this);
        
        // Read public guilds
        int guildCount = r.getNextInt();
        guildIDs = new int[guildCount];
        guildNames = new String[guildCount];
        memberCounts = new int[guildCount];
        treasuries = new long[guildCount];
        crestShapes = new int[guildCount];
        crestColors = new int[guildCount];
        crestEmblems = new int[guildCount];
        
        for (int i = 0; i < guildCount; i++) {
            guildIDs[i] = r.getNextInt();
            guildNames[i] = r.getNextString();
            memberCounts[i] = r.getNextInt();
            treasuries[i] = r.getNextLong();
            crestShapes[i] = r.getNextInt();
            crestColors[i] = r.getNextInt();
            crestEmblems[i] = r.getNextInt();
        }
        
        // Read pending invitations
        int inviteCount = r.getNextInt();
        inviteGuildIDs = new int[inviteCount];
        inviteGuildNames = new String[inviteCount];
        
        for (int i = 0; i < inviteCount; i++) {
            inviteGuildIDs[i] = r.getNextInt();
            inviteGuildNames[i] = r.getNextString();
        }
    }

    public PacketGuildsListResponse(
            int[] guildIDs,
            String[] guildNames,
            int[] memberCounts,
            long[] treasuries,
            int[] crestShapes,
            int[] crestColors,
            int[] crestEmblems,
            int[] inviteGuildIDs,
            String[] inviteGuildNames) {
        
        this.guildIDs = guildIDs;
        this.guildNames = guildNames;
        this.memberCounts = memberCounts;
        this.treasuries = treasuries;
        this.crestShapes = crestShapes;
        this.crestColors = crestColors;
        this.crestEmblems = crestEmblems;
        this.inviteGuildIDs = inviteGuildIDs;
        this.inviteGuildNames = inviteGuildNames;
        
        // Write packet
        PacketWriter w = new PacketWriter(this);
        
        w.putNextInt(guildIDs.length);
        for (int i = 0; i < guildIDs.length; i++) {
            w.putNextInt(guildIDs[i]);
            w.putNextString(guildNames[i]);
            w.putNextInt(memberCounts[i]);
            w.putNextLong(treasuries[i]);
            w.putNextInt(crestShapes[i]);
            w.putNextInt(crestColors[i]);
            w.putNextInt(crestEmblems[i]);
        }
        
        w.putNextInt(inviteGuildIDs.length);
        for (int i = 0; i < inviteGuildIDs.length; i++) {
            w.putNextInt(inviteGuildIDs[i]);
            w.putNextString(inviteGuildNames[i]);
        }
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        try {
            ModLogger.info("PacketGuildsListResponse.processClient: Received %d guilds, %d invites", 
                guildIDs.length, inviteGuildIDs.length);
            
            // Show the browser
            GuildBrowserForm.showBrowser(
                client,
                guildIDs, guildNames, memberCounts, treasuries,
                crestShapes, crestColors, crestEmblems,
                inviteGuildIDs, inviteGuildNames
            );
            
        } catch (Exception e) {
            ModLogger.error("Error processing guild list response", e);
        }
    }
}

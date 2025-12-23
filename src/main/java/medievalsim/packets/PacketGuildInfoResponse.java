/*
 * PacketGuildInfoResponse - Server sends full guild info to client
 * Part of Medieval Sim Mod guild management system.
 */
package medievalsim.packets;

import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.guilds.GuildRank;
import medievalsim.guilds.ui.GuildInfoPanelForm;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;

import java.util.ArrayList;
import java.util.List;

/**
 * Server->Client packet containing full guild information.
 * Used to populate the Guild Info Panel UI.
 */
public class PacketGuildInfoResponse extends Packet {

    // Guild basic info
    public int guildID;
    public String guildName;
    public String description;
    public boolean isPublic;
    public long treasury;
    
    // Crest design
    public int crestShape;
    public int crestPrimaryColor;
    public int crestSecondaryColor;
    public int crestEmblem;
    public int crestEmblemColor;
    public int crestBorder;
    
    // Members
    public long[] memberAuths;
    public String[] memberNames;
    public int[] memberRanks;
    public boolean[] memberOnline;
    
    // Audit log
    public String[] auditEntries;
    public long[] auditTimestamps;
    
    // Requester info (for determining their rank on client side)
    public long requesterAuth;

    public PacketGuildInfoResponse(byte[] data) {
        super(data);
        PacketReader r = new PacketReader(this);
        
        // Basic info
        this.guildID = r.getNextInt();
        this.guildName = r.getNextString();
        this.description = r.getNextString();
        this.isPublic = r.getNextBoolean();
        this.treasury = r.getNextLong();
        this.requesterAuth = r.getNextLong();
        
        // Crest
        this.crestShape = r.getNextInt();
        this.crestPrimaryColor = r.getNextInt();
        this.crestSecondaryColor = r.getNextInt();
        this.crestEmblem = r.getNextInt();
        this.crestEmblemColor = r.getNextInt();
        this.crestBorder = r.getNextInt();
        
        // Members
        int memberCount = r.getNextInt();
        this.memberAuths = new long[memberCount];
        this.memberNames = new String[memberCount];
        this.memberRanks = new int[memberCount];
        this.memberOnline = new boolean[memberCount];
        
        for (int i = 0; i < memberCount; i++) {
            this.memberAuths[i] = r.getNextLong();
            this.memberNames[i] = r.getNextString();
            this.memberRanks[i] = r.getNextInt();
            this.memberOnline[i] = r.getNextBoolean();
        }
        
        // Audit log
        int auditCount = r.getNextInt();
        this.auditEntries = new String[auditCount];
        this.auditTimestamps = new long[auditCount];
        
        for (int i = 0; i < auditCount; i++) {
            this.auditEntries[i] = r.getNextString();
            this.auditTimestamps[i] = r.getNextLong();
        }
    }

    public PacketGuildInfoResponse(
            int guildID,
            String guildName,
            String description,
            boolean isPublic,
            long treasury,
            GuildSymbolDesign symbol,
            long[] memberAuths,
            String[] memberNames,
            int[] memberRanks,
            boolean[] memberOnline,
            String[] auditEntries,
            long[] auditTimestamps,
            long requesterAuth) {
        
        this.guildID = guildID;
        this.guildName = guildName;
        this.description = description;
        this.isPublic = isPublic;
        this.treasury = treasury;
        this.requesterAuth = requesterAuth;
        
        // Symbol
        this.crestShape = symbol.getShape();
        this.crestPrimaryColor = symbol.getPrimaryColor();
        this.crestSecondaryColor = symbol.getSecondaryColor();
        this.crestEmblem = symbol.getEmblem();
        this.crestEmblemColor = symbol.getEmblemColor();
        this.crestBorder = symbol.getBorder();
        
        // Members
        this.memberAuths = memberAuths;
        this.memberNames = memberNames;
        this.memberRanks = memberRanks;
        this.memberOnline = memberOnline;
        
        // Audit
        this.auditEntries = auditEntries;
        this.auditTimestamps = auditTimestamps;
        
        // Write packet
        PacketWriter w = new PacketWriter(this);
        
        w.putNextInt(guildID);
        w.putNextString(guildName);
        w.putNextString(description);
        w.putNextBoolean(isPublic);
        w.putNextLong(treasury);
        w.putNextLong(requesterAuth);
        
        w.putNextInt(crestShape);
        w.putNextInt(crestPrimaryColor);
        w.putNextInt(crestSecondaryColor);
        w.putNextInt(crestEmblem);
        w.putNextInt(crestEmblemColor);
        w.putNextInt(crestBorder);
        
        w.putNextInt(memberAuths.length);
        for (int i = 0; i < memberAuths.length; i++) {
            w.putNextLong(memberAuths[i]);
            w.putNextString(memberNames[i]);
            w.putNextInt(memberRanks[i]);
            w.putNextBoolean(memberOnline[i]);
        }
        
        w.putNextInt(auditEntries.length);
        for (int i = 0; i < auditEntries.length; i++) {
            w.putNextString(auditEntries[i]);
            w.putNextLong(auditTimestamps[i]);
        }
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        try {
            ModLogger.debug("Received guild info for guild %d (%s)", guildID, guildName);
            
            // Build member data list
            List<GuildInfoPanelForm.MemberData> members = new ArrayList<>();
            for (int i = 0; i < memberAuths.length; i++) {
                members.add(new GuildInfoPanelForm.MemberData(
                    memberAuths[i],
                    memberNames[i],
                    GuildRank.fromLevel(memberRanks[i]),
                    memberOnline[i]
                ));
            }
            
            // Build symbol design
            GuildSymbolDesign symbol = new GuildSymbolDesign(
                crestShape, crestPrimaryColor, crestSecondaryColor,
                crestEmblem, crestEmblemColor, crestBorder
            );
            
            // Show the panel
            GuildInfoPanelForm.showPanel(
                client,
                guildID,
                guildName,
                description,
                isPublic,
                treasury,
                symbol,
                members,
                auditEntries,
                auditTimestamps,
                requesterAuth
            );
            
        } catch (Exception e) {
            ModLogger.error("Error processing guild info response", e);
        }
    }
}

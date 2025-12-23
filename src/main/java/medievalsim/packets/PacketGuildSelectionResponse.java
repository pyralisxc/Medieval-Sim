/*
 * PacketGuildSelectionResponse - Server sends list of guilds player belongs to
 * Part of Medieval Sim Mod guild management system.
 * Client opens GuildSelectionForm with this data.
 */
package medievalsim.packets;

import medievalsim.guilds.GuildRank;
import medievalsim.guilds.ui.GuildSelectionForm;
import medievalsim.guilds.ui.GuildSelectionForm.GuildEntry;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;

import java.util.ArrayList;
import java.util.List;

/**
 * Server->Client packet with list of guilds the player belongs to.
 * Triggers opening of GuildSelectionForm.
 */
public class PacketGuildSelectionResponse extends Packet {

    public List<GuildEntry> guilds;

    public PacketGuildSelectionResponse(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        
        int count = reader.getNextInt();
        guilds = new ArrayList<>(count);
        
        for (int i = 0; i < count; i++) {
            int guildID = reader.getNextInt();
            String guildName = reader.getNextString();
            int rankLevel = reader.getNextInt();
            int memberCount = reader.getNextInt();
            
            GuildRank rank = GuildRank.fromLevel(rankLevel);
            guilds.add(new GuildEntry(guildID, guildName, rank, memberCount));
        }
    }

    public PacketGuildSelectionResponse(List<PacketRequestGuildSelection.GuildSelectionEntry> entries) {
        PacketWriter writer = new PacketWriter(this);
        
        writer.putNextInt(entries.size());
        
        for (PacketRequestGuildSelection.GuildSelectionEntry entry : entries) {
            writer.putNextInt(entry.guildID);
            writer.putNextString(entry.guildName);
            writer.putNextInt(entry.rankLevel);
            writer.putNextInt(entry.memberCount);
        }
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        try {
            if (guilds == null || guilds.isEmpty()) {
                ModLogger.debug("Received empty guild selection response");
                return;
            }
            
            if (guilds.size() == 1) {
                // Only one guild - skip selection and go straight to info
                ModLogger.debug("Only one guild, requesting info directly");
                client.network.sendPacket(new PacketRequestGuildInfo(guilds.get(0).guildID));
            } else {
                // Multiple guilds - show selection form
                ModLogger.debug("Showing guild selection form with %d guilds", guilds.size());
                GuildSelectionForm.show(client, guilds);
            }
        } catch (Exception e) {
            ModLogger.error("Error processing guild selection response", e);
        }
    }
}

/*
 * PacketAcceptGuildUnlockQuest - Client accepts the guild unlock quest
 * Part of Medieval Sim Mod guild management system.
 * Per docs: Sent when player clicks Accept on QuestOfferContainer.
 */
package medievalsim.packets;

import medievalsim.guilds.GuildUnlockUtil;
import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Client->Server packet to accept the guild unlock quest.
 * Server creates quest objective for the unlock boss kill.
 */
public class PacketAcceptGuildUnlockQuest extends Packet {

    public PacketAcceptGuildUnlockQuest(byte[] data) {
        super(data);
        // No data needed
    }

    public PacketAcceptGuildUnlockQuest() {
        // No data to write
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Check if already unlocked
            if (GuildUnlockUtil.hasUnlockedGuilds(client)) {
                client.sendChatMessage(Localization.translate("ui", "guildquestoffer.alreadyunlocked"));
                ModLogger.debug("Player %s already has guilds unlocked", client.getName());
                return;
            }
            
            // Check if quest requires a boss
            if (!GuildUnlockUtil.requiresBossKill()) {
                // No boss required - just inform them
                client.sendChatMessage(Localization.translate("ui", "guildquestoffer.nobossrequired"));
                return;
            }
            
            // Accept quest - for now, just inform player what they need to do
            // In full implementation, this would create a tracked quest
            String bossName = GuildUnlockUtil.getUnlockBossDisplayName();
            String message = Localization.translate("ui", "guildquestoffer.questaccepted", "boss", bossName);
            client.sendChatMessage(message);
            
            ModLogger.info("Player %s accepted guild unlock quest (boss: %s)", 
                client.getName(), bossName);
                
            // TODO: Full quest system integration would track this as an active quest
            // For now, the unlock check in GuildUnlockUtil.hasUnlockedGuilds() 
            // will automatically detect when the boss is killed
                
        } catch (Exception e) {
            ModLogger.error("Error accepting guild unlock quest", e);
        }
    }
}

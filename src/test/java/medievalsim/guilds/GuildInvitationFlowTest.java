package medievalsim.guilds;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GuildInvitationFlowTest {

    @Test
    public void inviteAndAcceptAddsMember() {
        // Ensure test config allows guild creation
        medievalsim.config.ModConfig.Guilds.maxGuildsPerPlayer = 10;

        // Ensure world data registries are registered so GuildManager constructor can run
        medievalsim.registries.MedievalSimWorldData.registerCore();

        GuildManager gm = new GuildManager();
        long founder = 100L;
        long invitee = 200L;

        // Create guild (use direct construction to avoid global test-state issues)
        GuildData guild = new GuildData(1, "Knights", founder);
        try {
            java.lang.reflect.Field guildsField = GuildManager.class.getDeclaredField("guilds");
            guildsField.setAccessible(true);
            java.util.Map<Integer, GuildData> guilds = (java.util.Map<Integer, GuildData>) guildsField.get(gm);
            guilds.put(1, guild);

            java.lang.reflect.Field nameIndexField = GuildManager.class.getDeclaredField("guildNameIndex");
            nameIndexField.setAccessible(true);
            java.util.Map<String, Integer> nameIndex = (java.util.Map<String, Integer>) nameIndexField.get(gm);
            nameIndex.put("knights", 1);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Failed to inject guild via reflection: " + e.toString());
        }

        int gid = guild.getGuildID();

        // Send invite
        boolean sent = gm.sendInvitation(gid, invitee, founder);
        assertTrue(sent, "Invitation should be sent");

        // Ensure invitation is recorded
        assertTrue(gm.getPlayerData(invitee).hasInvitation(gid));

        // Accept invite
        boolean accepted = gm.acceptInvitation(gid, invitee);
        assertTrue(accepted, "Invite should be accepted");

        // Verify membership
        assertTrue(gm.getGuild(gid).isMember(invitee));
        assertEquals(GuildRank.RECRUIT, gm.getGuild(gid).getMemberRank(invitee));
    }
}

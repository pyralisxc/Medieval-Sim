package medievalsim.zones.domain;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class GuildZoneTest {

    @Test
    public void saveAndLoadPreservesProperties() {
        GuildZone zone = new GuildZone(5, "Test Zone", 123456L, 180, 42);
        zone.setGuildName("Knights");
        zone.setAllowNonMembers(true);
        zone.setPurchasable(true, 1000L);
        zone.addAllyGuild(7);
        zone.addAllyGuild(9);

        SaveData save = new SaveData("zone_save");
        zone.addSaveData(save);

        GuildZone loaded = new GuildZone();
        loaded.applyLoadData(save.toLoadData());

        assertEquals(zone.getGuildID(), loaded.getGuildID());
        assertEquals(zone.getGuildName(), loaded.getGuildName());
        assertEquals(zone.isAllowNonMembers(), loaded.isAllowNonMembers());
        assertEquals(zone.isPurchasable(), loaded.isPurchasable());
        assertEquals(zone.getPurchaseCost(), loaded.getPurchaseCost());

        Set<Integer> allies = loaded.getAllyGuildIDs();
        assertTrue(allies.contains(7));
        assertTrue(allies.contains(9));
        assertEquals(2, allies.size());
    }

    @Test
    public void addAndRemoveAlliesWorks() {
        GuildZone zone = new GuildZone();
        zone.addAllyGuild(11);
        zone.addAllyGuild(22);
        assertTrue(zone.getAllyGuildIDs().contains(11));
        assertTrue(zone.getAllyGuildIDs().contains(22));

        zone.removeAllyGuild(11);
        assertFalse(zone.getAllyGuildIDs().contains(11));
        assertTrue(zone.getAllyGuildIDs().contains(22));
    }
}

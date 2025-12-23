package medievalsim.guilds;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import necesse.entity.objectEntity.ObjectEntity;
import medievalsim.guilds.objects.GuildBannerObjectEntity;

public class GuildBannerHelperTest {

    @Test
    public void countPlayerBannersInList_countsCorrectly() {
        java.util.List<GuildBannerHelper.BannerSpec> specs = new ArrayList<>();
        specs.add(new GuildBannerHelper.BannerSpec(5, 100L));
        specs.add(new GuildBannerHelper.BannerSpec(5, 200L));
        specs.add(new GuildBannerHelper.BannerSpec(5, 100L));

        int count = GuildBannerHelper.countPlayerBannersInListFromSpecs(specs, 5, 100L);
        assertEquals(2, count);

        int countOther = GuildBannerHelper.countPlayerBannersInListFromSpecs(specs, 5, 200L);
        assertEquals(1, countOther);

        int countNo = GuildBannerHelper.countPlayerBannersInListFromSpecs(specs, 6, 100L);
        assertEquals(0, countNo);
    }
}

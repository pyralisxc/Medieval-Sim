package medievalsim.guilds;

import necesse.entity.objectEntity.ObjectEntity;
import medievalsim.guilds.objects.GuildBannerObjectEntity;

import java.util.List;

public final class GuildBannerHelper {
    private GuildBannerHelper() {}

    /**
     * Simple spec used for lightweight testing or external data sources
     */
    public static class BannerSpec {
        public final int guildID;
        public final long placedByAuth;
        public BannerSpec(int guildID, long placedByAuth) {
            this.guildID = guildID;
            this.placedByAuth = placedByAuth;
        }
    }

    /**
     * Count how many banners in the provided entities list belong to the given guild
     * and were placed by the given player auth.
     */
    public static int countPlayerBannersInList(List<ObjectEntity> entities, int guildID, long playerAuth) {
        if (entities == null) return 0;
        int count = 0;
        for (ObjectEntity oe : entities) {
            if (!(oe instanceof GuildBannerObjectEntity)) continue;
            GuildBannerObjectEntity b = (GuildBannerObjectEntity) oe;
            if (b.getGuildID() == guildID && b.getPlacedByAuth() == playerAuth) count++;
        }
        return count;
    }

    /**
     * Count how many BannerSpecs match guildID and playerAuth (lightweight for testing)
     */
    public static int countPlayerBannersInListFromSpecs(java.util.List<BannerSpec> specs, int guildID, long playerAuth) {
        if (specs == null) return 0;
        int count = 0;
        for (BannerSpec s : specs) {
            if (s.guildID == guildID && s.placedByAuth == playerAuth) count++;
        }
        return count;
    }
}

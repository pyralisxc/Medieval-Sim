/*
 * Guild Artisan Mob for Medieval Sim Mod
 * NPC that handles guild administration tasks and sells guild-related items.
 * 
 * Extends HumanShop to provide:
 * - Standard settler behavior (jobs, housing, combat)
 * - Shop functionality for guild items
 * - Proper interaction handling to open guild UI
 */
package medievalsim.guilds.mobs;

import medievalsim.util.ModLogger;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.MobRegistry;
import necesse.entity.mobs.ai.behaviourTree.BehaviourTreeAI;
import necesse.entity.mobs.ai.behaviourTree.trees.HumanAI;
import necesse.entity.mobs.ai.behaviourTree.util.AIMover;
import necesse.entity.mobs.friendly.human.humanShop.HumanShop;
import necesse.entity.mobs.friendly.human.humanShop.SellingShopItem;
import necesse.inventory.InventoryItem;

/**
 * Guild Artisan NPC - handles guild creation, management, and related services.
 * 
 * The Guild Artisan is the gateway NPC for all guild-related features:
 * - Create new guilds
 * - Join existing guilds  
 * - Purchase guild items (teleport bottles, banners, etc.)
 * - Manage guild memberships
 * 
 * Limited to ONE per settlement (enforced in spawn logic).
 */
import necesse.entity.mobs.HumanTexture;

public class GuildArtisanMob extends HumanShop {
    // Texture reference for spawn item / icon usage on client
    public static HumanTexture TEXTURE;

    public static void loadTextures() {
        // Register/load the human texture for this mob so spawn item icons and particles can reference it
        TEXTURE = MobRegistry.Textures.humanTexture("guildartisan");
    }

    public GuildArtisanMob() {
        // Health values: 500 non-settler, 500 settler (matches Elder and other NPCs)
        super(500, 500, "guildartisan");
        this.setSpeed(30.0f);
        this.attackCooldown = 500;
        this.attackAnimTime = 500;
        this.setSwimSpeed(1.0f);
        
        // Guild Artisan can be recruited but prefers administrative work
        this.canJoinAdventureParties = false;
        
        // Give default equipment (copper sword for defense)
        this.equipmentInventory.setItem(6, new InventoryItem("coppersword"));
        
        // Register basic shop items
        this.shop.addSellingItem("guildbanner", new SellingShopItem()).setRandomPrice(100, 150);
        this.shop.addSellingItem("guildcrest", new SellingShopItem()).setRandomPrice(50, 100);
    }
    
    @Override
    public void init() {
        super.init();
        // Standard human AI with longer idle time (administrative work)
        this.ai = new BehaviourTreeAI<GuildArtisanMob>(this, new HumanAI(480, true, true, 25000), new AIMover(humanPathIterations));
    }

    // === Interaction ===
    // Note: We don't override interact() - HumanShop.interact() handles everything
    // and calls getOpenShopPacket() below to get our custom container packet.
    
    /**
     * Creates the packet to open the Guild Artisan UI.
     * Called by HumanShop.interact() when a player interacts with this NPC.
     */
    @Override
    public PacketOpenContainer getOpenShopPacket(Server server, ServerClient client) {
        ModLogger.info("getOpenShopPacket called for player");
        
        // Get the player's current guild ID (or -1 if not in a guild)
        int playerGuildID = getPlayerGuildID(client);
        
        // Check if player has unlocked guilds (boss kill requirement)
        boolean hasUnlockedGuilds = medievalsim.guilds.GuildUnlockUtil.hasUnlockedGuilds(client);
        
        // Create packet with player's guild info
        Packet content = new Packet();
        PacketWriter writer = new PacketWriter(content);
        writer.putNextInt(playerGuildID);
        
        // Also send the player's rank level (0 if none) and whether plot flags are enabled on server
        int playerRankLevel = 0;
        if (playerGuildID >= 0) {
            var guild = medievalsim.guilds.GuildManager.get(client.getServer().world).getGuild(playerGuildID);
            if (guild != null) {
                var rank = guild.getMemberRank(client.authentication);
                if (rank != null) playerRankLevel = rank.level;
            }
        }
        writer.putNextInt(playerRankLevel);
        writer.putNextBoolean(medievalsim.config.ModConfig.Settlements.plotFlagsEnabled);
        writer.putNextBoolean(hasUnlockedGuilds);
        
        ModLogger.info("Opening GuildArtisanContainer with ID=%d, playerGuildID=%d, rank=%d, plotsEnabled=%b, unlocked=%b", 
            GuildArtisanContainer.CONTAINER_ID, playerGuildID, playerRankLevel, medievalsim.config.ModConfig.Settlements.plotFlagsEnabled, hasUnlockedGuilds);
        
        return new PacketOpenContainer(GuildArtisanContainer.CONTAINER_ID, content);
    }
    
    /**
     * Gets the guild ID for a player, or -1 if not in any guild.
     */
    private int getPlayerGuildID(ServerClient client) {
        try {
            medievalsim.guilds.GuildManager gm = medievalsim.guilds.GuildManager.get(client.getServer().world);
            if (gm == null) return -1;
            
            medievalsim.guilds.GuildData guild = gm.getPlayerPrimaryGuild(client.authentication);
            return guild != null ? guild.getGuildID() : -1;
        } catch (Exception e) {
            ModLogger.error("Failed to get player guild ID", e);
            return -1;
        }
    }

    // === Registration ===

    public static void registerMob() {
        // Parameters: stringID, mobClass, countKillStat, isBossMob, displayName, killHint, createSpawnItem
        MobRegistry.registerMob(
            "guildartisan", 
            GuildArtisanMob.class, 
            false,  // countKillStat - don't track kills
            false,  // isBossMob
            new LocalMessage("mob", "guildartisan"),  // display name
            null,   // killHint
            false    // createSpawnItem - handled manually to allow custom spawn icon
        );
        ModLogger.info("Registered guild artisan mob");
    }
    
    // === Settlement Limit Enforcement ===
    
    /**
     * Check if a Guild Artisan can spawn in a settlement (limit one per settlement).
     * TODO: Implement when settlement integration is complete.
     */
    public static boolean canSpawnInSettlement(/* ServerSettlementData settlement */) {
        // Placeholder - will check if settlement already has a guild artisan
        return true;
    }
}

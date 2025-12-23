package medievalsim.packets;

import medievalsim.util.ModLogger;
import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.guilds.GuildManager;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Client -> Server: Request to create a new guild
 */
public class PacketCreateGuild extends Packet {
    public String guildName;
    public String description;
    public boolean isPublic;
    public GuildSymbolDesign symbolDesign;

    public PacketCreateGuild(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildName = reader.getNextString();
        this.description = reader.getNextString();
        this.isPublic = reader.getNextBoolean();
        
        // Read symbol design
        this.symbolDesign = new GuildSymbolDesign();
        this.symbolDesign.setBackgroundShape(reader.getNextInt());
        this.symbolDesign.setPrimaryColor(reader.getNextInt());
        this.symbolDesign.setSecondaryColor(reader.getNextInt());
        this.symbolDesign.setEmblemID(reader.getNextInt());
        this.symbolDesign.setEmblemColor(reader.getNextInt());
        this.symbolDesign.setBorderStyle(reader.getNextInt());
    }

    public PacketCreateGuild(String guildName) {
        this(guildName, "", true, new GuildSymbolDesign());
    }
    
    public PacketCreateGuild(String guildName, String description, boolean isPublic) {
        this(guildName, description, isPublic, new GuildSymbolDesign());
    }
    
    public PacketCreateGuild(String guildName, String description, boolean isPublic, GuildSymbolDesign symbol) {
        this.guildName = guildName;
        this.description = description != null ? description : "";
        this.isPublic = isPublic;
        this.symbolDesign = symbol != null ? symbol : new GuildSymbolDesign();
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextString(guildName);
        writer.putNextString(this.description);
        writer.putNextBoolean(isPublic);
        
        // Write symbol design
        writer.putNextInt(symbolDesign.getBackgroundShape());
        writer.putNextInt(symbolDesign.getPrimaryColor());
        writer.putNextInt(symbolDesign.getSecondaryColor());
        writer.putNextInt(symbolDesign.getEmblemID());
        writer.putNextInt(symbolDesign.getEmblemColor());
        writer.putNextInt(symbolDesign.getBorderStyle());
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            if (client == null || client.getServer() == null) return;

            GuildManager gm = medievalsim.guilds.GuildManager.get(client.getServer().world);
            if (gm == null) {
                client.sendChatMessage("Guild system not available on server.");
                return;
            }

            // Validate name
            if (!gm.isValidGuildName(guildName)) {
                client.sendChatMessage("Invalid guild name. Names must be 3-32 chars and use letters/numbers/spaces/_-'");
                return;
            }
            if (gm.isGuildNameTaken(guildName)) {
                client.sendChatMessage("That guild name is already taken.");
                return;
            }

            // Handle cost: deduct gold from player's inventory before creating the guild
            long guildCost = medievalsim.config.ModConfig.Guilds.guildCreationCost;
            if (guildCost > 0) {
                if (client.playerMob == null || client.playerMob.getLevel() == null) {
                    client.sendChatMessage("Unable to process payment for guild creation.");
                    return;
                }
                necesse.level.maps.Level level = client.playerMob.getLevel();
                necesse.inventory.item.Item coinItem = necesse.engine.registries.ItemRegistry.getItem("coin");
                if (coinItem == null) {
                    client.sendChatMessage("System error - coin item not found.");
                    return;
                }

                int playerGold = client.playerMob.getInv().main.getAmount(level, client.playerMob, coinItem, "createGuild");
                if (playerGold < guildCost) {
                    client.sendChatMessage("Not enough gold! Need " + guildCost + " gold to create a guild.");
                    return;
                }

                int removed = client.playerMob.getInv().main.removeItems(level, client.playerMob, coinItem, (int)guildCost, "createGuild");
                if (removed < guildCost) {
                    client.sendChatMessage("Failed to withdraw gold for guild creation.");
                    return;
                }
            }

            // Attempt create with description, visibility, and crest
            medievalsim.guilds.GuildData guild = gm.createGuild(guildName, client.authentication, description, isPublic);
            if (guild == null) {
                client.sendChatMessage("Guild creation failed. You may not have permission or limits were reached.");
                ModLogger.warn("Guild creation failed for %s: %s", client.getName(), this.guildName);
                // Refund cost if we removed it
                if (guildCost > 0 && client.playerMob != null && client.playerMob.getLevel() != null) {
                    necesse.level.maps.Level level = client.playerMob.getLevel();
                    necesse.inventory.InventoryItem refund = new necesse.inventory.InventoryItem("coin", (int)guildCost);
                    client.playerMob.getInv().main.addItem(level, client.playerMob, refund, "refund", null);
                }
                return;
            }
            
            // Apply symbol design if provided, otherwise create default
            if (symbolDesign == null) {
                // Create default symbol design
                symbolDesign = new GuildSymbolDesign(0, 0x0000FF, 0x000000, 0, 0xFFFFFF, 1);
            }
            guild.setSymbolDesign(symbolDesign);
            
            ModLogger.info("Created guild with symbol: bg=%d, primary=%X, emblem=%d", 
                symbolDesign.getBackgroundShape(), symbolDesign.getPrimaryColor(), symbolDesign.getEmblemID());

            // Give starter guild items to founder: crest, banner (item), and flag (placeable object)
            try {
                boolean anyLeftover = false;
                if (client.playerMob != null && client.playerMob.getLevel() != null) {
                    necesse.level.maps.Level level = client.playerMob.getLevel();

                    // Create inventory items with guild data
                    necesse.inventory.InventoryItem crestItem = new necesse.inventory.InventoryItem("guildcrest", 1);
                    necesse.inventory.InventoryItem banner = new necesse.inventory.InventoryItem("guildbanner", 1);
                    necesse.inventory.InventoryItem flag = new necesse.inventory.InventoryItem("guildflag", 1);

                    // Set guild data on each item
                    medievalsim.guilds.items.GuildCrestItem.setGuildID(crestItem, guild.getGuildID());
                    medievalsim.guilds.items.GuildCrestItem.setSymbolDesign(crestItem, symbolDesign);
                    
                    // Banner and flag are now objects, use their ObjectItem methods
                    medievalsim.guilds.objects.GuildBannerObjectItem.setGuildID(banner, guild.getGuildID());
                    medievalsim.guilds.objects.GuildBannerObjectItem.setSymbolDesign(banner, symbolDesign);
                    
                    medievalsim.guilds.items.GuildFlagObjectItem.setGuildID(flag, guild.getGuildID());
                    medievalsim.guilds.items.GuildFlagObjectItem.setSymbolDesign(flag, symbolDesign);

                    // Try to add all three
                    client.playerMob.getInv().main.addItem(level, client.playerMob, crestItem, "createGuild", null);
                    if (crestItem.getAmount() > 0) anyLeftover = true;

                    client.playerMob.getInv().main.addItem(level, client.playerMob, banner, "createGuild", null);
                    if (banner.getAmount() > 0) anyLeftover = true;

                    client.playerMob.getInv().main.addItem(level, client.playerMob, flag, "createGuild", null);
                    if (flag.getAmount() > 0) anyLeftover = true;

                    if (anyLeftover) {
                        // Inventory was full for at least one item — refund the creation cost as a fallback
                        necesse.inventory.item.Item coinItem = necesse.engine.registries.ItemRegistry.getItem("coin");
                        if (coinItem != null) {
                            necesse.inventory.InventoryItem refund = new necesse.inventory.InventoryItem(coinItem, (int)medievalsim.config.ModConfig.Guilds.guildCreationCost);
                            client.playerMob.getInv().main.addItem(level, client.playerMob, refund, "refund", null);
                        }
                        client.sendChatMessage("Guild created but one or more starter items could not be added to your inventory; refund issued for guild item.");
                    } else {
                        client.sendChatMessage("Guild created! You received a guild crest, banner, and flag in your inventory.");
                    }
                }
            } catch (Exception e) {
                ModLogger.warn("Failed to give guild starter items to %s: %s", client.getName(), e.getMessage());
            }

            // Success - notify creator and announce to others
            client.sendPacket(new PacketGuildCreated(guild.getGuildID(), guild.getName(), true));
            server.network.sendToAllClientsExcept(new PacketGuildCreated(guild.getGuildID(), guild.getName(), false), client);
            ModLogger.info("Guild created: %s (ID: %d) by %s, public=%b", 
                guild.getName(), guild.getGuildID(), client.getName(), isPublic);

        } catch (Exception e) {
            client.sendChatMessage("An unexpected error occurred while creating guild.");
            ModLogger.error("Exception in PacketCreateGuild.processServer", e);
        }
    }
}

package medievalsim.commandcenter.wrapper;

import medievalsim.commandcenter.CommandCategory;
import medievalsim.commandcenter.cache.CommandMetadataCache;
import medievalsim.util.ModLogger;
import necesse.engine.commands.ChatCommand;
import necesse.engine.commands.CommandsManager;
import necesse.engine.commands.ModularChatCommand;
import necesse.engine.commands.PermissionLevel;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for Necesse's built-in commands with metadata for UI wrapper generation.
 * 
 * STRATEGIC FOCUS: After analysis of Necesse's F10 debug menu, this registry now focuses
 * exclusively on server administration and complex workflow commands where Command Center
 * provides unique value. Basic creative commands (give, buff, time, rain, etc.) are 
 * intentionally excluded as F10 debug menu handles them superior.
 * 
 * This scans Necesse's CommandsManager and extracts metadata for selected commands,
 * organizing them by category for the Command Center UI.
 */
public class NecesseCommandRegistry {
    
    private static final Map<String, NecesseCommandMetadata> commands = new ConcurrentHashMap<>();
    private static final Map<CommandCategory, List<NecesseCommandMetadata>> commandsByCategory = new ConcurrentHashMap<>();
    private static boolean initialized = false;
    
    /**
     * Initialize the registry by scanning Necesse's CommandsManager.
     * Uses intelligent caching to reduce reflection overhead on subsequent calls.
     */
    public static void initialize() {
        if (initialized) {
            ModLogger.warn("NecesseCommandRegistry already initialized");
            return;
        }
        
        // Try to use cached commands first
        CommandMetadataCache cache = CommandMetadataCache.getInstance();
        if (cache.isCacheValid()) {
            Map<String, NecesseCommandMetadata> cachedCommands = cache.getCachedCommands();
            
            for (NecesseCommandMetadata metadata : cachedCommands.values()) {
                registerCommand(metadata);
            }
            
            initialized = true;
            ModLogger.debug("Loaded %d cached commands (skipped reflection scan)", cachedCommands.size());
            return;
        }
        
        // Cache miss - perform full reflection scan
        ModLogger.debug("Cache miss - scanning Necesse commands via reflection...");
        
        try {
            // Get all commands from CommandsManager via reflection
            Field serverCommandsField = CommandsManager.class.getDeclaredField("serverCommands");
            serverCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<ChatCommand> necesseCommands = (List<ChatCommand>) serverCommandsField.get(null);
            
            int scanned = 0;
            int parsed = 0;
            Map<String, NecesseCommandMetadata> newCommands = new HashMap<>();
            
            for (ChatCommand cmd : necesseCommands) {
                scanned++;
                
                // Only process ModularChatCommand (skip simple/custom commands)
                if (!(cmd instanceof ModularChatCommand)) {
                    continue;
                }
                
                ModularChatCommand modCmd = (ModularChatCommand) cmd;
                
                // Determine category based on command name/purpose
                CommandCategory category = categorizeCommand(modCmd);
                
                // Skip low-priority/debug commands for now
                if (category == null) {
                    continue;
                }
                
                // Parse metadata
                NecesseCommandMetadata metadata = NecesseCommandMetadata.fromNecesseCommand(modCmd, category);
                
                if (metadata != null) {
                    registerCommand(metadata);
                    newCommands.put(metadata.getId(), metadata);
                    parsed++;
                }
            }
            
            // Cache the results for future use
            cache.cacheCommands(newCommands);
            
            initialized = true;
            ModLogger.debug("Registered %d/%d Necesse commands for UI wrapper across %d categories",
                    parsed, scanned, commandsByCategory.size());
            
        } catch (Exception e) {
            ModLogger.error("Failed to initialize NecesseCommandRegistry", e);
        }
    }
    
    /**
     * Register a command metadata in the registry.
     */
    private static void registerCommand(NecesseCommandMetadata metadata) {
        commands.put(metadata.getId(), metadata);
        
        commandsByCategory
                .computeIfAbsent(metadata.getCategory(), k -> new ArrayList<>())
                .add(metadata);
    }
    
    /**
     * Categorize a command based on its name and purpose.
     * Returns null for commands we don't want to wrap (handled better by F10 debug menu or low-priority).
     * 
     * STRATEGIC FILTERING: Focus on server administration and complex workflows,
     * avoid competing with F10 debug menu's creative tools.
     */
    private static CommandCategory categorizeCommand(ModularChatCommand cmd) {
        String name = cmd.name.toLowerCase();
        
        // ❌ EXCLUDE: Basic creative commands (F10 debug menu superior)
        // These are handled much better by the F10 debug menu's visual interface
        if (name.equals("give") || name.equals("buff") || name.equals("clearbuff") || 
            name.equals("health") || name.equals("maxhealth") || name.equals("mana") || 
            name.equals("maxmana") || name.equals("heal") || name.equals("levels") || 
            name.equals("hunger") || name.equals("invincibility") || name.equals("rain") || 
            name.equals("time") || name.equals("difficulty") || name.equals("creativemode") ||
            name.equals("hp") || name.equals("maxhp") || name.equals("setlevel") || 
            name.equals("die") || name.equals("healmobs")) {
            return null; // Skip - F10 debug menu handles these better
        }
        
        // ❌ EXCLUDE: Simple world editing (F10 debug menu has better tools)
        if (name.equals("clearmobs") || name.equals("cleardrops") || name.equals("mow")) {
            return null; // Skip - F10 debug menu superior for creative world editing
        }
        
        // ✅ KEEP: Complex teleportation (workflow advantage over F10)
        if (name.equals("setposition") || name.equals("teleport")) {
            return CommandCategory.TELEPORT;
        }
        
        // ✅ KEEP: Complex inventory operations (batch/administrative operations)
        if (name.equals("clearall") || name.equals("copyinventory") || 
            name.equals("copyitem") || name.equals("upgradeitem")) {
            return CommandCategory.INVENTORY;
        }
        
        // ❌ EXCLUDE: Creative inventory commands (F10 debug menu handles better)
        if (name.equals("armorset") || name.equals("enchant")) {
            return null; // Skip - F10 debug menu superior for creative inventory
        }
        
        // ✅ KEEP: Administrative player management (not creative tools)
        if (name.equals("regen") || name.equals("staticdamage") || name.equals("resilience") || 
            name.equals("resethealthupgrades")) {
            return CommandCategory.PLAYER_STATS;
        }
        
        // ✅ KEEP: Administrative world management (server operations, not creative)
        if (name.equals("allowcheats") || name.equals("deathpenalty") || name.equals("setdimension") || 
            name.equals("setisland")) {
            return CommandCategory.WORLD;
        }
        
        // ✅ KEEP: Communication & Teams (server management, combined category)
        if (name.equals("say") || name.equals("me") || name.equals("whisper") || name.equals("w") ||
            name.equals("pm") || name.equals("createteam") || name.equals("inviteteam") || 
            name.equals("leaveteam") || name.equals("setteam") || name.equals("setteamowner") || 
            name.equals("clearteam") || name.equals("getteam")) {
            return CommandCategory.TEAMS;
        }
        
        // ✅ HIGH PRIORITY: Server Administration (ZERO F10 debug overlap - our core strength)
        if (name.equals("save") || name.equals("stop") || name.equals("kick") || name.equals("ban") ||
            name.equals("unban") || name.equals("bans") || name.equals("password") ||
            name.equals("motd") || name.equals("permissions") || name.equals("pausewhenempty") ||
            name.equals("maxlatency") || name.equals("settings") || name.equals("changename") ||
            name.equals("deleteplayerdata") || name.equals("settlements") || 
            name.equals("spawnvisitor") || name.equals("spawnsettler") || name.equals("setlanguage")) {
            return CommandCategory.SERVER_ADMIN;
        }
        
        // ✅ KEEP: Raids & Events (server management, not creative tools)
        if (name.equals("startraid") || name.equals("endraid") || name.equals("completeincursion") ||
            name.equals("raids") || name.equals("setraiddiff") || name.equals("setraidtier") ||
            name.equals("startraidattack")) {
            return CommandCategory.RAIDS;
        }
        
        // ✅ KEEP: Complex world operations (administrative operations only)
        if (name.equals("clearevents") || name.equals("clearconnection")) {
            return CommandCategory.WORLD_EDITING;
        }
        
        // ❌ EXCLUDE: Creative world editing (F10 debug menu handles better)
        if (name.equals("cleararea")) {
            return null; // Skip - F10 debug menu superior for area clearing
        }
        
        // ❌ EXCLUDE: Debug/low-priority commands AND creative duplicates
        if (name.equals("help") || name.equals("stophelp") || name.equals("whisperhelp") ||
            name.equals("print") || name.equals("performance") || name.equals("network") ||
            name.equals("playernames") || name.equals("players") || name.equals("playtime") ||
            name.equals("myperms") || name.equals("itemgnd")) {
            return null; // Skip debug/info commands
        }
        
        // Default to OTHER for unknown commands
        return CommandCategory.OTHER;
    }
    
    // Public API
    
    public static NecesseCommandMetadata getCommand(String id) {
        return commands.get(id);
    }
    
    public static Collection<NecesseCommandMetadata> getAllCommands() {
        return commands.values();
    }
    
    public static List<NecesseCommandMetadata> getCommandsByCategory(CommandCategory category) {
        return commandsByCategory.getOrDefault(category, Collections.emptyList());
    }
    
    public static List<NecesseCommandMetadata> getCommandsByPermission(PermissionLevel minLevel) {
        List<NecesseCommandMetadata> result = new ArrayList<>();
        for (NecesseCommandMetadata cmd : commands.values()) {
            if (cmd.getPermission().ordinal() >= minLevel.ordinal()) {
                result.add(cmd);
            }
        }
        return result;
    }
    
    public static Set<CommandCategory> getAvailableCategories() {
        return commandsByCategory.keySet();
    }
    
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Get statistics about registered commands.
     */
    public static Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total_commands", commands.size());
        stats.put("total_categories", commandsByCategory.size());
        
        for (Map.Entry<CommandCategory, List<NecesseCommandMetadata>> entry : commandsByCategory.entrySet()) {
            stats.put("category_" + entry.getKey().name(), entry.getValue().size());
        }
        
        return stats;
    }
}

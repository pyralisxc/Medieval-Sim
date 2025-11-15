package medievalsim.commandcenter;

import necesse.engine.commands.PermissionLevel;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Wrapper around Necesse's command system for Command Center integration.
 * Provides structured parameter definitions and execution flow.
 */
public abstract class AdminCommand {
    
    private final String id;
    private final String displayName;
    private final String description;
    private final PermissionLevel requiredPermission;
    private final CommandCategory category;
    private final boolean requiresConfirmation;
    private final WorldType worldType;
    
    protected AdminCommand(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.requiredPermission = builder.requiredPermission;
        this.category = builder.category;
        this.requiresConfirmation = builder.requiresConfirmation;
        this.worldType = builder.worldType;
    }
    
    // Getters
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public PermissionLevel getRequiredPermission() { return requiredPermission; }
    public CommandCategory getCategory() { return category; }
    public boolean requiresConfirmation() { return requiresConfirmation; }
    public WorldType getWorldType() { return worldType; }
    
    /**
     * Execute the command with validated parameters
     * @return CommandResult with success status and message
     */
    public abstract CommandResult execute(Client client, Server server, ServerClient executor, Object[] args);
    
    /**
     * Validate if the executor has permission to run this command
     */
    public boolean hasPermission(ServerClient executor) {
        if (executor == null) return false;
        PermissionLevel level = executor.getPermissionLevel();
        return level != null && level.getLevel() >= requiredPermission.getLevel();
    }
    
    /**
     * Check if this command is available in the given world
     * Based on creative mode state
     */
    public boolean isAvailableInWorld(necesse.engine.network.server.Server server) {
        if (server == null || server.world == null || server.world.settings == null) {
            return true; // Default to available
        }
        
        boolean isCreative = server.world.settings.creativeMode;
        
        switch (worldType) {
            case SURVIVAL_ONLY:
            case REQUIRES_SURVIVAL:
                return !isCreative; // Hide if creative mode is enabled
            case CREATIVE_ONLY:
                return isCreative;  // Only show if creative mode is enabled
            default:
                return true;        // ANY - always available
        }
    }
    
    /**
     * Builder pattern for creating AdminCommands
     */
    public static class Builder {
        private String id;
        private String displayName;
        private String description = "";
        private PermissionLevel requiredPermission = PermissionLevel.ADMIN;
        private CommandCategory category = CommandCategory.OTHER;
        private boolean requiresConfirmation = false;
        private WorldType worldType = WorldType.ANY;
        
        public Builder(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder permission(PermissionLevel level) {
            this.requiredPermission = level;
            return this;
        }
        
        public Builder category(CommandCategory category) {
            this.category = category;
            return this;
        }
        
        public Builder requiresConfirmation(boolean requires) {
            this.requiresConfirmation = requires;
            return this;
        }
        
        public Builder requiresConfirmation() {
            return requiresConfirmation(true);
        }
        
        public Builder worldType(WorldType type) {
            this.worldType = type;
            return this;
        }
        
        /**
         * Build the AdminCommand. Note: This returns a Builder, not the final command.
         * The actual command must be created by extending AdminCommand with the Builder.
         */
        public Builder build() {
            return this;
        }
    }
}

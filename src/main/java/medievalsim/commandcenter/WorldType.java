package medievalsim.commandcenter;

/**
 * Filters commands based on world creative mode state
 * Following Necesse's pattern: all worlds start as survival, creative mode is toggled on
 */
public enum WorldType {
    ANY,                    // Available in all worlds
    SURVIVAL_ONLY,          // Hide if creative mode is enabled (prevents creative-enabling commands)
    CREATIVE_ONLY,          // Only show if creative mode is already enabled
    REQUIRES_SURVIVAL       // Same as SURVIVAL_ONLY (semantic alias for commands that would enable creative)
}

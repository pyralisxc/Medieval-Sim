package medievalsim.commandcenter.domain;

/**
 * Types of command parameters for UI rendering
 */
public enum ParameterType {
    PLAYER,           // Dropdown of online players
    ITEM,             // Filterable item dropdown
    NUMBER_INT,       // Integer spinner with min/max
    NUMBER_FLOAT,     // Float spinner with min/max (decimal numbers)
    STRING,           // Text input field
    BOOLEAN,          // Checkbox or toggle
    LOCATION,         // Coordinate input or location picker (legacy, use COORDINATE)
    COORDINATE,       // Coordinate input with world-click support (supports "100", "%+10", "%-5")
    BUFF,             // Buff dropdown (autocomplete from BuffRegistry)
    MOB,              // Mob type dropdown
    TIME,             // Time value (for /time command)
    PERMISSION_LEVEL, // Permission level dropdown
    PRESET            // Preset dropdown (fixed choices, like TimeCommand presets)
}

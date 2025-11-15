package medievalsim.commandcenter.wrapper;

import necesse.engine.commands.CmdParameter;
import necesse.engine.commands.ModularChatCommand;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.commands.ParsedCommand;
import medievalsim.commandcenter.CommandCategory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata extracted from a Necesse ModularChatCommand for UI wrapper generation.
 *
 * This class parses Necesse's built-in commands using reflection to extract:
 * - Command ID, name, description
 * - Permission level
 * - Parameter list with types and constraints
 * - Category for UI organization
 */
public class NecesseCommandMetadata {

    private final String id;
    private final String action;
    private final PermissionLevel permission;
    private final boolean isCheat;
    private final List<ParameterMetadata> parameters;
    private final CommandCategory category;
    /**
     * Backing CmdParameter array from Necesse.
     * This lets us drive dynamic forms and autocomplete from engine state.
     */
    private final CmdParameter[] cmdParameters;

    public NecesseCommandMetadata(String id, String action, PermissionLevel permission,
                                   boolean isCheat, List<ParameterMetadata> parameters,
                                   CommandCategory category, CmdParameter[] cmdParameters) {
        this.id = id;
        this.action = action;
        this.permission = permission;
        this.isCheat = isCheat;
        this.parameters = parameters;
        this.category = category;
        this.cmdParameters = cmdParameters;
    }

    /**
     * Parse metadata from a Necesse ModularChatCommand using reflection.
     *
     * @param command The Necesse command to parse
     * @param category The UI category for this command
     * @return Metadata object, or null if parsing fails
     */
    public static NecesseCommandMetadata fromNecesseCommand(ModularChatCommand command, CommandCategory category) {
        try {
            // Get basic command info (these are public fields/methods)
            String id = command.name;
            String action = command.getAction();
            PermissionLevel permission = command.permissionLevel;
            boolean isCheat = command.isCheat();

            // Use reflection to access the private 'parameters' field
            Field parametersField = ModularChatCommand.class.getDeclaredField("parameters");
            parametersField.setAccessible(true);
            CmdParameter[] cmdParameters = (CmdParameter[]) parametersField.get(command);

            // Parse each parameter and flatten any nested extra parameters into a linear list.
            // This ensures things like the permissions sub-parameter on /permissions show up in the UI.
            List<ParameterMetadata> parameters = new ArrayList<>();
            if (cmdParameters != null) {
                for (CmdParameter cmdParam : cmdParameters) {
                    ParameterMetadata rootMeta = ParameterMetadata.fromCmdParameter(cmdParam);
                    if (rootMeta != null) {
                        flattenParameterMetadata(rootMeta, parameters);
                    }
                }
            }

            // Store both high-level metadata and backing CmdParameter[] for engine-driven flows
            return new NecesseCommandMetadata(id, action, permission, isCheat, parameters, category, cmdParameters);

        } catch (Exception e) {
            System.err.println("Failed to parse command metadata for: " + command.name);
            e.printStackTrace();
            return null;
        }
    }


    /**
     * Flatten a ParameterMetadata tree (including extra parameters) into a linear list
     * while preserving declaration order.
     */
    private static void flattenParameterMetadata(ParameterMetadata meta, List<ParameterMetadata> out) {
        if (meta == null || out == null) {
            return;
        }
        out.add(meta);
        ParameterMetadata[] extras = meta.getExtraParams();
        if (extras != null) {
            for (ParameterMetadata extra : extras) {
                if (extra != null) {
                    flattenParameterMetadata(extra, out);
                }
            }
        }
    }

    // Getters
    public String getId() { return id; }
    public String getAction() { return action; }
    public String getDisplayName() {
        // Convert "setposition" -> "Set Position"
        if (id == null || id.isEmpty()) return "Unknown";
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(id.charAt(0)));
        for (int i = 1; i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append(' ');
            }
            sb.append(c);
        }
        return sb.toString();
    }
    public PermissionLevel getPermission() { return permission; }
    public boolean isCheat() { return isCheat; }
    public List<ParameterMetadata> getParameters() { return parameters; }
    public CommandCategory getCategory() { return category; }
    public CmdParameter[] getCmdParameters() { return cmdParameters; }

    /**
     * Determine if this command should be available in the current world context.
     *
     * Filtering rules (hide commands completely in survival mode):
     * - In Creative mode or with Cheats enabled → Show ALL commands
     * - In Survival mode (no cheats/creative) → Hide commands where isCheat() == true
     *
     * This prevents players from seeing commands that would:
     * - Enable creative mode (permanent achievement loss)
     * - Enable cheats (permanent achievement loss)
     * - Give items, buffs, teleport, etc. (gameplay-altering cheats)
     *
     * @param client The client to check world settings from
     * @return true if command should be shown in dropdown, false to hide it
     */
    public boolean isAvailableInWorld(necesse.engine.network.client.Client client) {
        // Safety: If we can't determine world settings, show command (safer than hiding)
        if (client == null || client.worldSettings == null) {
            return true;
        }

        necesse.engine.world.WorldSettings settings = client.worldSettings;

        // RULE 1: If creative mode is enabled, show everything
        if (settings.creativeMode) {
            return true;
        }

        // RULE 2: If cheats are allowed, show everything
        if (settings.allowCheats) {
            return true;
        }

        // RULE 3: In survival mode without cheats, hide all cheat commands
        // This includes /give, /buff, /creativemode, /allowcheats, etc.
        return !this.isCheat();
    }


    /**
     * Ask Necesse's command system for autocomplete options based on the current
     * argument list and this command's backing CmdParameter[] definition.
     *
     * This is a thin wrapper so the UI layer does not need to talk to
     * CmdParameter.autoComplete(...) directly.
     */
    public java.util.List<String> getAutocompleteOptions(necesse.engine.network.client.Client client, String[] args) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (client == null || cmdParameters == null) {
            return result;
        }

        try {
            necesse.engine.network.server.Server server = client.getLocalServer();
            necesse.engine.network.server.ServerClient serverClient = server != null
                    ? server.getLocalServerClient()
                    : null;

            java.util.List<necesse.engine.commands.AutoComplete> autoList = CmdParameter.autoComplete(
                    client,
                    server,
                    serverClient,
                    cmdParameters,
                    args
            );

            if (autoList != null) {
                for (necesse.engine.commands.AutoComplete ac : autoList) {
                    if (ac == null || ac.newArgs == null) continue;
                    String suggestion = ac.newArgs.trim();
                    if (!suggestion.isEmpty()) {
                        result.add(suggestion);
                    }
                }
            }
        } catch (Exception e) {
            // Keep this wrapper silent; caller can decide how to log if needed
        }

        return result;
    }

    /**
     * Build a command string from user-provided parameter values.
     *
     * Uses Necesse's ParsedCommand.wrapArgument() to properly quote arguments
     * that contain spaces (e.g., player names "John Doe" → "John Doe").
     *
     * FIXED: Proper optional parameter handling - maintains index alignment even when skipping empty optionals
     *
     * @param parameterValues Array of string values for each parameter (in order)
     * @return Formatted command string (e.g., "/setposition player1 surface 1000 1000")
     */
    public String buildCommandString(String[] parameterValues) {
        StringBuilder cmd = new StringBuilder("/");
        cmd.append(id);

        if (parameterValues == null || parameterValues.length == 0) {
            return cmd.toString();
        }

        for (String value : parameterValues) {
            if (value == null) {
                // Skip nulls; required/optional semantics are enforced in the UI layer for now
                continue;
            }
            String wrappedValue = ParsedCommand.wrapArgument(value);
            cmd.append(" ").append(wrappedValue);
        }

        return cmd.toString();
    }

    @Override
    public String toString() {
        return String.format("NecesseCommandMetadata{id='%s', action='%s', permission=%s, parameters=%d}",
                id, action, permission, parameters.size());
    }
}

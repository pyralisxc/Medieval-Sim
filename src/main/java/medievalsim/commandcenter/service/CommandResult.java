package medievalsim.commandcenter.service;

/**
 * Result of a command execution
 */
public class CommandResult {
    
    private final boolean success;
    private final String message;
    private final Object data; // Optional data to return
    
    private CommandResult(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
    
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Object getData() { return data; }
    
    // Factory methods
    public static CommandResult success(String message) {
        return new CommandResult(true, message, null);
    }
    
    public static CommandResult success(String message, Object data) {
        return new CommandResult(true, message, data);
    }
    
    public static CommandResult error(String message) {
        return new CommandResult(false, message, null);
    }
    
    public static CommandResult permissionDenied() {
        return new CommandResult(false, "Permission denied", null);
    }
}

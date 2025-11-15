package medievalsim.commandcenter.history;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tracks command execution history for smart defaults and recent command list
 */
public class CommandHistory {
    
    private static final int MAX_HISTORY_SIZE = 100;
    private static final int TOP_N_SIZE = 3;
    
    private static final Queue<CommandExecution> recentCommands = new ConcurrentLinkedQueue<>();
    private static final Map<String, Queue<String>> recentParametersByType = new HashMap<>();
    
    /**
     * Record a command execution
     */
    public static void recordExecution(String commandId, String commandName, Map<String, Object> parameters) {
        CommandExecution execution = new CommandExecution(commandId, commandName, parameters, System.currentTimeMillis());
        
        recentCommands.offer(execution);
        
        // Trim to max size
        while (recentCommands.size() > MAX_HISTORY_SIZE) {
            recentCommands.poll();
        }
        
        // Record individual parameters for smart defaults
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String paramName = entry.getKey();
            Object value = entry.getValue();
            
            if (value != null) {
                String valueStr = value.toString();
                recordParameter(paramName, valueStr);
            }
        }
    }
    
    /**
     * Record a parameter value for smart defaults
     */
    private static void recordParameter(String paramName, String value) {
        Queue<String> history = recentParametersByType.computeIfAbsent(paramName, k -> new LinkedList<>());
        
        // Remove if already exists (to move to front)
        history.remove(value);
        
        // Add to front
        ((LinkedList<String>) history).addFirst(value);
        
        // Trim to max size
        while (history.size() > MAX_HISTORY_SIZE) {
            ((LinkedList<String>) history).removeLast();
        }
    }
    
    /**
     * Get recent command executions
     */
    public static List<CommandExecution> getRecentCommands(int limit) {
        List<CommandExecution> recent = new ArrayList<>(recentCommands);
        Collections.reverse(recent); // Most recent first
        return recent.subList(0, Math.min(limit, recent.size()));
    }
    
    /**
     * Get top N recent values for a parameter (smart defaults)
     */
    public static List<String> getTop3ForParameter(String paramName) {
        Queue<String> history = recentParametersByType.get(paramName);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> top3 = new ArrayList<>();
        Iterator<String> iterator = history.iterator();
        int count = 0;
        while (iterator.hasNext() && count < TOP_N_SIZE) {
            top3.add(iterator.next());
            count++;
        }
        
        return top3;
    }
    
    /**
     * Get top 3 recently targeted players
     */
    public static List<String> getTop3Players() {
        return getTop3ForParameter("player");
    }
    
    /**
     * Get top 3 recently used items
     */
    public static List<String> getTop3Items() {
        return getTop3ForParameter("item");
    }
    
    /**
     * Clear all history
     */
    public static void clear() {
        recentCommands.clear();
        recentParametersByType.clear();
    }
    
    /**
     * Represents a single command execution
     */
    public static class CommandExecution {
        private final String commandId;
        private final String commandName;
        private final Map<String, Object> parameters;
        private final long timestamp;
        
        public CommandExecution(String commandId, String commandName, Map<String, Object> parameters, long timestamp) {
            this.commandId = commandId;
            this.commandName = commandName;
            this.parameters = new HashMap<>(parameters);
            this.timestamp = timestamp;
        }
        
        public String getCommandId() { return commandId; }
        public String getCommandName() { return commandName; }
        public Map<String, Object> getParameters() { return parameters; }
        public long getTimestamp() { return timestamp; }
        
        /**
         * Get a formatted display string for this execution
         */
        public String getDisplayString() {
            StringBuilder sb = new StringBuilder();
            sb.append(commandName);
            
            if (!parameters.isEmpty()) {
                sb.append(" (");
                boolean first = true;
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(entry.getValue());
                    first = false;
                }
                sb.append(")");
            }
            
            return sb.toString();
        }
    }
}

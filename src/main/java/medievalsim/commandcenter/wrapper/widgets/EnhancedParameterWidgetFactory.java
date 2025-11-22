package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.commandcenter.wrapper.ParameterMetadata.ParameterHandlerType;
import medievalsim.commandcenter.wrapper.widgets.strategy.*;
import medievalsim.util.ModLogger;
import necesse.engine.network.client.Client;

import java.util.List;
import java.util.ArrayList;

/**
 * Refactored ParameterWidgetFactory using Strategy Pattern.
 * 
 * This replaces the complex switch statement with a clean, extensible strategy system.
 * Each parameter type category has its own strategy, making the code more maintainable
 * and easier to extend.
 * 
 * BENEFITS:
 * - Single Responsibility: Each strategy handles one category of widgets
 * - Open/Closed Principle: Easy to add new strategies without modifying existing code
 * - Strategy Pattern: Clean separation of widget creation logic
 * - Maintainability: No more massive switch statements
 */
public class EnhancedParameterWidgetFactory {
    
    // Ordered list of strategies (checked in priority order)
    private static final List<WidgetCreationStrategy> strategies = new ArrayList<>();
    
    static {
        // Initialize strategies in priority order
        // More specific strategies first, fallback last
        strategies.add(new StringWidgetStrategy());
        strategies.add(new NumberWidgetStrategy());
        strategies.add(new PlayerTeamWidgetStrategy());
        strategies.add(new SpecializedWidgetStrategy());
        strategies.add(new FallbackWidgetStrategy()); // Always last
    }
    
    /**
     * Create the appropriate widget for a parameter using strategy pattern.
     * 
     * @param parameter The parameter metadata
     * @param x X position for the widget
     * @param y Y position for the widget
     * @param client The client instance (needed for some widgets)
     * @param commandId The command ID for context-aware creation
     * @return A ParameterWidget instance
     */
    public static ParameterWidget createWidget(ParameterMetadata parameter, int x, int y, Client client, String commandId) {
        // Find the first strategy that can handle this parameter
        for (WidgetCreationStrategy strategy : strategies) {
            if (strategy.canHandle(parameter)) {
                try {
                    return strategy.createWidget(parameter, x, y, client, commandId);
                } catch (Exception e) {
                    ModLogger.error("Failed to create widget using strategy %s for parameter %s: %s",
                            strategy.getClass().getSimpleName(), parameter.getName(), e.getMessage());
                    // Continue to next strategy
                }
            }
        }
        
        // This should never happen since FallbackWidgetStrategy handles everything
        ModLogger.error("No strategy could handle parameter: %s", parameter.getName());
        return new TextInputWidget(parameter, x, y, 200, null);
    }
    
    /**
     * Create widget without command context (backward compatibility).
     */
    public static ParameterWidget createWidget(ParameterMetadata parameter, int x, int y, Client client) {
        return createWidget(parameter, x, y, client, null);
    }
    
    /**
     * Create widget without client reference (backward compatibility).
     */
    public static ParameterWidget createWidget(ParameterMetadata parameter, int x, int y) {
        return createWidget(parameter, x, y, null, null);
    }
    
    /**
     * Get the recommended height for a widget type using strategies.
     * 
     * @param parameter The parameter metadata
     * @return Recommended height in pixels
     */
    public static int getWidgetHeight(ParameterMetadata parameter) {
        // Find the strategy that handles this parameter
        for (WidgetCreationStrategy strategy : strategies) {
            if (strategy.canHandle(parameter)) {
                return strategy.getRecommendedHeight();
            }
        }
        
        return 30; // Default height
    }
    
    /**
     * Get the recommended height for a parameter type (legacy method).
     */
    public static int getWidgetHeight(ParameterHandlerType type) {
        // For compatibility, map type to strategy recommendation
        switch (type) {
            case MULTI:
                return 30; // Could be enhanced to return strategy-specific height
            default:
                return 30;
        }
    }
    
    /**
     * Add a custom strategy to the factory.
     * Useful for mods that want to extend widget creation.
     * 
     * @param strategy The strategy to add
     * @param priority Insert position (0 = highest priority, strategies.size() = lowest)
     */
    public static void addStrategy(WidgetCreationStrategy strategy, int priority) {
        if (priority < 0) {
            priority = 0;
        } else if (priority > strategies.size() - 1) {
            // Insert before fallback strategy
            priority = strategies.size() - 1;
        }
        
        strategies.add(priority, strategy);
        ModLogger.info("Added custom widget strategy: %s at priority %d", 
                strategy.getClass().getSimpleName(), priority);
    }
    
    /**
     * Get information about registered strategies (for debugging).
     */
    public static String getStrategyInfo() {
        StringBuilder sb = new StringBuilder("Registered Widget Strategies:\n");
        for (int i = 0; i < strategies.size(); i++) {
            sb.append(String.format("  %d. %s\n", i + 1, strategies.get(i).getClass().getSimpleName()));
        }
        return sb.toString();
    }
}

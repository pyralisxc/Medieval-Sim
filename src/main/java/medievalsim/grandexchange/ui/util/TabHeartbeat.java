package medievalsim.grandexchange.ui.util;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.input.InputEvent;
import necesse.engine.input.controller.ControllerEvent;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.controller.ControllerFocus;
import necesse.gfx.forms.controller.ControllerNavigationHandler;
import necesse.gfx.forms.components.FormComponent;

/**
 * A reusable heartbeat component for Grand Exchange tabs.
 * 
 * <p>This invisible FormComponent periodically executes registered tasks during
 * its draw cycle. Use it to keep dynamic UI elements (cooldown labels, status
 * indicators, prices) up-to-date without requiring manual refresh.</p>
 * 
 * <h3>Usage:</h3>
 * <pre>{@code
 * TabHeartbeat heartbeat = new TabHeartbeat();
 * host.addComponent(heartbeat);
 * 
 * // Register tasks to run on each heartbeat
 * heartbeat.registerTask(() -> updateCooldownLabel(label));
 * heartbeat.registerTask(() -> refreshOrderStatus(order));
 * }</pre>
 * 
 * <p>The heartbeat interval is controlled by {@link ModConfig.GrandExchange#uiHeartbeatIntervalMs}.</p>
 */
public final class TabHeartbeat extends FormComponent {
    
    private final List<Runnable> tasks = new ArrayList<>();
    private final LongSupplier intervalSupplier;
    private long lastRunMillis;
    
    /**
     * Creates a heartbeat using the configured interval from ModConfig.
     */
    public TabHeartbeat() {
        this(() -> ModConfig.GrandExchange.uiHeartbeatIntervalMs);
    }
    
    /**
     * Creates a heartbeat with a custom interval supplier.
     * Useful for testing or specialized refresh rates.
     * 
     * @param intervalSupplier supplies the interval in milliseconds
     */
    public TabHeartbeat(LongSupplier intervalSupplier) {
        this.intervalSupplier = intervalSupplier;
    }
    
    /**
     * Registers a task to be executed on each heartbeat.
     * Tasks are executed in registration order.
     * 
     * @param task the task to run; null tasks are ignored
     */
    public void registerTask(Runnable task) {
        if (task != null) {
            tasks.add(task);
        }
    }
    
    /**
     * Clears all registered tasks.
     * Useful when rebuilding the tab UI.
     */
    public void clearTasks() {
        tasks.clear();
    }
    
    /**
     * Returns the number of registered tasks.
     */
    public int getTaskCount() {
        return tasks.size();
    }
    
    @Override
    public void handleInputEvent(InputEvent event, TickManager tickManager, PlayerMob perspective) {
        // No-op: passive heartbeat does not process input.
    }
    
    @Override
    public void handleControllerEvent(ControllerEvent event, TickManager tickManager, PlayerMob perspective) {
        // No-op: controller input is ignored.
    }
    
    @Override
    public void addNextControllerFocus(List<ControllerFocus> list,
                                        int currentXOffset,
                                        int currentYOffset,
                                        ControllerNavigationHandler customNavigationHandler,
                                        Rectangle area,
                                        boolean draw) {
        // No-op: component is invisible and cannot gain focus.
    }

    /**
     * Skip render box intersection check since this component has no visual bounds.
     * Without this, the component would never be drawn because its bounding box
     * (0,0,0,0) doesn't intersect the visible render area.
     */
    @Override
    public boolean shouldSkipRenderBoxCheck() {
        return true;
    }

    @Override
    public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
        long now = System.currentTimeMillis();
        long interval = intervalSupplier.getAsLong();

        if (now - lastRunMillis < interval) {
            return;
        }
        lastRunMillis = now;

        for (Runnable task : tasks) {
            try {
                task.run();
            } catch (RuntimeException ex) {
                ModLogger.warn("Tab heartbeat task failed: %s", ex.getMessage());
            }
        }
    }    @Override
    public List<Rectangle> getHitboxes() {
        return Collections.emptyList();
    }
}

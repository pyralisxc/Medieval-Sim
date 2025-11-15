package medievalsim.commandcenter.worldclick;

import necesse.engine.GlobalData;
import necesse.engine.input.Control;
import necesse.engine.input.InputEvent;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.state.State;
import necesse.gfx.camera.GameCamera;

/**
 * Control that handles world clicks during coordinate selection mode.
 * This is registered as a passive control that intercepts left-clicks when world-click selection is active.
 */
public class WorldClickControl extends Control {
    
    public WorldClickControl() {
        super(-100, "medievalsim_worldclick", (GameMessage) new StaticMessage("World Click Selection"));
    }
    
    @Override
    public void activate(InputEvent event) {
        super.activate(event);
        
        WorldClickHandler handler = WorldClickHandler.getInstance();
        
        // Only handle if world-click selection is active and this is a press (not release)
        if (!handler.isActive() || !this.isPressed()) {
            return;
        }
        
        State currentState = GlobalData.getCurrentState();
        if (currentState == null) {
            return;
        }
        
        GameCamera camera = currentState.getCamera();
        if (camera == null) {
            return;
        }
        
        // Get clicked tile position
        int tileX = camera.getMouseLevelTilePosX(event);
        int tileY = camera.getMouseLevelTilePosY(event);
        
        // Let handler process the click
        boolean consumed = handler.handleWorldClick(tileX, tileY);
        
        // If click was consumed, mark event as used
        if (consumed) {
            event.use();
            
            // Clean up if selection ended
            if (!handler.isActive()) {
                WorldClickIntegration.stopIntegration();
            }
        }
    }
}

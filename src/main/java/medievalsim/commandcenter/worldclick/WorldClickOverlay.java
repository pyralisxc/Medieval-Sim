package medievalsim.commandcenter.worldclick;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.Renderer;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawables.SortedDrawable;
import necesse.gfx.gameFont.FontManager;
import necesse.gfx.gameFont.FontOptions;
import necesse.level.maps.hudManager.HudDrawElement;

import java.awt.Color;
import java.util.List;

/**
 * HUD overlay that renders visual feedback during world-click coordinate selection.
 * 
 * Displays:
 * - Highlighted tile at cursor position (bright yellow)
 * - Coordinate label at mouse position
 * 
 * Pattern: Extends HudDrawElement and adds SortedDrawable instances to the render list.
 */
public class WorldClickOverlay extends HudDrawElement {
    
    // Colors
    private static final Color HIGHLIGHT_EDGE = new Color(255, 255, 0, 255); // Bright yellow edge
    private static final Color HIGHLIGHT_FILL = new Color(255, 255, 0, 100); // Semi-transparent yellow fill
    private static final Color LABEL_COLOR = Color.WHITE;
    private static final Color LABEL_BG = new Color(0, 0, 0, 180); // Semi-transparent black background
    
    public WorldClickOverlay() {
        // HudDrawElement constructor
    }
    
    @Override
    public void addDrawables(List<SortedDrawable> list, GameCamera camera, PlayerMob perspective) {
        WorldClickHandler handler = WorldClickHandler.getInstance();
        
        if (!handler.isActive()) {
            return; // Not in selection mode, don't render
        }
        
        final int hoverX = handler.getHoverTileX();
        final int hoverY = handler.getHoverTileY();
        
        if (hoverX < 0 || hoverY < 0) {
            return; // No hover position yet
        }
        
        // Draw highlighted tile at cursor
        list.add(new SortedDrawable() {
            @Override
            public int getPriority() {
                return -99999; // Draw on top of zones but below UI
            }
            
            @Override
            public void draw(TickManager tickManager) {
                // Get screen position for tile
                int drawX = camera.getTileDrawX(hoverX);
                int drawY = camera.getTileDrawY(hoverY);
                
                // Draw filled rectangle for tile highlight
                Renderer.initQuadDraw(32, 32)
                    .color(HIGHLIGHT_FILL)
                    .draw(drawX, drawY);
                
                // Draw edge outline (4 thin rectangles forming a border)
                int borderWidth = 2;
                // Top edge
                Renderer.initQuadDraw(32, borderWidth)
                    .color(HIGHLIGHT_EDGE)
                    .draw(drawX, drawY);
                // Bottom edge
                Renderer.initQuadDraw(32, borderWidth)
                    .color(HIGHLIGHT_EDGE)
                    .draw(drawX, drawY + 32 - borderWidth);
                // Left edge
                Renderer.initQuadDraw(borderWidth, 32)
                    .color(HIGHLIGHT_EDGE)
                    .draw(drawX, drawY);
                // Right edge
                Renderer.initQuadDraw(borderWidth, 32)
                    .color(HIGHLIGHT_EDGE)
                    .draw(drawX + 32 - borderWidth, drawY);
            }
        });
        
        // Draw coordinate label
        final String displayText = handler.getHoverDisplayString();
        if (displayText != null) {
            list.add(new SortedDrawable() {
                @Override
                public int getPriority() {
                    return -99998; // Draw label on top of highlight
                }
                
                @Override
                public void draw(TickManager tickManager) {
                    // Get screen position offset from tile
                    int drawX = camera.getTileDrawX(hoverX) + 36; // Offset to right of tile
                    int drawY = camera.getTileDrawY(hoverY) + 8;  // Offset down slightly
                    
                    // Setup font
                    FontOptions fontOptions = new FontOptions(16).color(LABEL_COLOR);
                    int textWidth = FontManager.bit.getWidthCeil(displayText, fontOptions);
                    int textHeight = FontManager.bit.getHeightCeil(displayText, fontOptions);
                    
                    // Draw background
                    int padding = 4;
                    Renderer.initQuadDraw(textWidth + padding * 2, textHeight + padding * 2)
                        .color(LABEL_BG)
                        .draw(drawX - padding, drawY - padding);
                    
                    // Draw text
                    FontManager.bit.drawString(
                        (float) drawX,
                        (float) drawY,
                        displayText,
                        fontOptions
                    );
                }
            });
        }
    }
}

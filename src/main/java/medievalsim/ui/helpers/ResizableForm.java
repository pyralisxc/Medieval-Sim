package medievalsim.ui.helpers;

import necesse.gfx.forms.Form;

import java.awt.Rectangle;

/**
 * Reusable base class for forms with edge-dragging resize functionality.
 *
 * This provides min/max bounds and a hook for future resize logic.
 * Full edge-dragging resize can be implemented when needed by extending Necesse's input event system.
 *
 * To use:
 * 1. Extend this class instead of Form
 * 2. Call super() with min/max/default dimensions
 * 3. Override onResize() to adjust your UI elements when form is resized
 */
public abstract class ResizableForm extends Form {
    
    // Resize configuration
    protected final int minWidth;
    protected final int minHeight;
    protected final int maxWidth;
    protected final int maxHeight;
    
    /**
     * Create a resizable form.
     * 
     * @param name Form identifier
     * @param minWidth Minimum width
     * @param minHeight Minimum height
     * @param maxWidth Maximum width
     * @param maxHeight Maximum height
     * @param defaultWidth Initial width
     * @param defaultHeight Initial height
     */
    public ResizableForm(String name, int minWidth, int minHeight, int maxWidth, int maxHeight, 
                         int defaultWidth, int defaultHeight) {
        super(name, defaultWidth, defaultHeight);
        
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        
        // Make form draggable by title bar
        this.setDraggingBox(new Rectangle(0, 0, defaultWidth, 30));
    }
    
    /**
     * Called when form is resized. Override to adjust UI layout.
     * 
     * @param oldWidth Previous width
     * @param oldHeight Previous height
     * @param newWidth New width
     * @param newHeight New height
     */
    protected abstract void onResize(int oldWidth, int oldHeight, int newWidth, int newHeight);
    
    /**
     * Manually resize the form (enforces bounds)
     */
    public void resize(int newWidth, int newHeight) {
        int oldWidth = this.getWidth();
        int oldHeight = this.getHeight();
        
        // Enforce bounds
        newWidth = Math.max(minWidth, Math.min(maxWidth, newWidth));
        newHeight = Math.max(minHeight, Math.min(maxHeight, newHeight));
        
        if (newWidth != oldWidth || newHeight != oldHeight) {
            this.setWidth(newWidth);
            this.setHeight(newHeight);
            onResize(oldWidth, oldHeight, newWidth, newHeight);
            
            // Update dragging box
            this.setDraggingBox(new Rectangle(0, 0, newWidth, 30));
        }
    }
    
    /**
     * Get minimum width
     */
    public int getMinWidth() {
        return minWidth;
    }
    
    /**
     * Get minimum height
     */
    public int getMinHeight() {
        return minHeight;
    }
    
    /**
     * Get maximum width
     */
    public int getMaxWidth() {
        return maxWidth;
    }
    
    /**
     * Get maximum height
     */
    public int getMaxHeight() {
        return maxHeight;
    }
}

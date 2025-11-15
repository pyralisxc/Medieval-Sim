package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import necesse.gfx.forms.components.FormComponent;

/**
 * Wrapper widget that adapts CoordinatePairWidget to ParameterWidget interface.
 * 
 * Allows a single CoordinatePairWidget to satisfy two ParameterMetadata requirements
 * (one for X coordinate, one for Y coordinate) while sharing the same UI widget.
 * 
 * This enables getValue() calls for both X and Y parameters to work correctly
 * with the command building system.
 */
public class CoordinatePairWrapperWidget extends ParameterWidget {
    
    private final CoordinatePairWidget coordWidget;
    private final boolean isXCoordinate; // true = X, false = Y
    
    /**
     * Create a wrapper for one coordinate of a pair
     * 
     * @param parameter The parameter metadata (X or Y)
     * @param coordWidget The shared coordinate pair widget
     * @param isXCoordinate true for X coordinate, false for Y coordinate
     */
    public CoordinatePairWrapperWidget(ParameterMetadata parameter, 
                                      CoordinatePairWidget coordWidget,
                                      boolean isXCoordinate) {
        super(parameter);
        this.coordWidget = coordWidget;
        this.isXCoordinate = isXCoordinate;
    }
    
    @Override
    public String getValue() {
        return isXCoordinate ? coordWidget.getXValue() : coordWidget.getYValue();
    }
    
    @Override
    public void setValue(String value) {
        if (isXCoordinate) {
            coordWidget.setXValue(value);
        } else {
            coordWidget.setYValue(value);
        }
    }
    
    @Override
    protected boolean validateValue() {
        // Validate the entire coordinate pair
        String error = coordWidget.validate();
        if (error != null) {
            validationError = error;
            return false;
        }
        return true;
    }
    
    @Override
    public FormComponent getComponent() {
        // Return the appropriate input component
        return isXCoordinate ? coordWidget.getXInput() : coordWidget.getYInput();
    }
    
    @Override
    public void reset() {
        // Reset both coordinates when either is reset
        coordWidget.reset();
    }
    
    @Override
    public void onFocus() {
        // No-op - focus handled by CoordinatePairWidget inputs
    }
}

package medievalsim.grandexchange.ui.components;

import necesse.gfx.forms.components.FormCheckBox;
import necesse.gfx.gameTooltips.GameTooltips;
import necesse.gfx.gameTooltips.StringTooltips;

import java.util.function.Supplier;

/**
 * FormCheckBox variant that defers tooltip text to a supplier so callers can
 * surface contextual messaging (cooldowns, pending actions, etc.) without
 * re-instantiating checkbox components.
 */
public class TooltipFormCheckBox extends FormCheckBox {
    private Supplier<String> tooltipSupplier;

    public TooltipFormCheckBox(String text, int x, int y, boolean checked) {
        super(text, x, y, checked);
    }

    public void setTooltipSupplier(Supplier<String> tooltipSupplier) {
        this.tooltipSupplier = tooltipSupplier;
    }

    public void clearTooltipSupplier() {
        this.tooltipSupplier = null;
    }

    @Override
    public GameTooltips getTooltip() {
        if (tooltipSupplier == null) {
            return null;
        }
        String text = tooltipSupplier.get();
        if (text == null || text.isEmpty()) {
            return null;
        }
        StringTooltips tooltips = new StringTooltips();
        tooltips.add(text);
        return tooltips;
    }
}

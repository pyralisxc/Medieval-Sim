package medievalsim.grandexchange.ui.components;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import medievalsim.grandexchange.net.GEFeedbackLevel;
import medievalsim.grandexchange.ui.viewmodel.FeedbackBus.FeedbackMessage;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.input.InputEvent;
import necesse.engine.input.controller.ControllerEvent;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.controller.ControllerFocus;
import necesse.gfx.forms.controller.ControllerNavigationHandler;

/**
 * Passive form component that mirrors the latest FeedbackBus message into a label.
 */
public final class FeedbackLabelBinder extends FormComponent {
    private final FormLabel label;
    private final Supplier<FeedbackMessage> supplier;
    private long lastTimestamp = -1L;
    private static final Color COLOR_SUCCESS = Color.BLACK;
    private static final Color COLOR_WARN = Color.BLACK;
    private static final Color COLOR_ERROR = Color.BLACK;
    private static final Color COLOR_DEFAULT = Color.BLACK;

    public FeedbackLabelBinder(FormLabel label, Supplier<FeedbackMessage> supplier) {
        this.label = Objects.requireNonNull(label, "label");
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }

    @Override
    public void handleInputEvent(InputEvent event, TickManager tickManager, PlayerMob perspective) {
        // No-op
    }

    @Override
    public void handleControllerEvent(ControllerEvent event, TickManager tickManager, PlayerMob perspective) {
        // No-op
    }

    @Override
    public void addNextControllerFocus(List<ControllerFocus> list,
                                       int currentXOffset,
                                       int currentYOffset,
                                       ControllerNavigationHandler customNavigationHandler,
                                       Rectangle area,
                                       boolean draw) {
        // Invisible binder cannot gain focus.
    }

    @Override
    public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
        FeedbackMessage latest = supplier.get();
        long timestamp = latest == null ? -1L : latest.timestamp();
        if (timestamp == lastTimestamp) {
            return;
        }
        lastTimestamp = timestamp;
        if (latest == null || latest.message() == null) {
            label.setText("");
            label.setColor(COLOR_DEFAULT);
            return;
        }
        label.setText(latest.message());
        label.setColor(resolveColor(latest.level()));
    }

    private Color resolveColor(GEFeedbackLevel level) {
        if (level == null) {
            return COLOR_DEFAULT;
        }
        return switch (level) {
            case ERROR -> COLOR_ERROR;
            case WARN -> COLOR_WARN;
            default -> COLOR_SUCCESS;
        };
    }

    @Override
    public List<Rectangle> getHitboxes() {
        return Collections.emptyList();
    }
}

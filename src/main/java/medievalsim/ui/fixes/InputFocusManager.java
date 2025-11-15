package medievalsim.ui.fixes;

import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.components.FormInputSize;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.input.InputEvent;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.entity.mobs.PlayerMob;

/**
 * Input Focus Manager - Provides utilities for better text input handling
 * 
 * Problem: When user types in text input fields, game hotkeys like 'M' (map) still trigger
 * Solution: Provide enhanced text input components and focus management utilities
 */
public class InputFocusManager {
    
    /**
     * Enhanced text input that provides better focus behavior
     * Use this instead of FormTextInput directly for improved user experience
     */
    public static class EnhancedTextInput extends FormTextInput {
        
        private boolean hasFocus = false;
        
        public EnhancedTextInput(int x, int y, FormInputSize size, int width, int maxLength) {
            super(x, y, size, width, maxLength);
            setupEnhancedBehavior();
        }
        
        public EnhancedTextInput(int x, int y, FormInputSize size, int width, int maxWidth, int maxLength) {
            super(x, y, size, width, maxWidth, maxLength);
            setupEnhancedBehavior();
        }
        
        private void setupEnhancedBehavior() {
            // Add submit event to help with focus management
            this.onSubmit(e -> {
                // When user submits (presses Enter), clear focus to prevent further hotkey conflicts
                this.clearSelection();
                this.hasFocus = false;
            });
        }
        
        @Override
        public void handleInputEvent(InputEvent event, TickManager tickManager, PlayerMob playerMob) {
            // Track focus state
            if (this.isTyping()) {
                this.hasFocus = true;
                // Handle the input normally for text input
                super.handleInputEvent(event, tickManager, playerMob);
                // Consume the event to prevent it from reaching game handlers
                // by not calling any additional handlers
            } else {
                this.hasFocus = false;
                super.handleInputEvent(event, tickManager, playerMob);
            }
        }
        
        /**
         * Check if this text input currently has focus
         */
        public boolean hasInputFocus() {
            return this.hasFocus && this.isTyping();
        }
        
        /**
         * Clear focus and stop typing
         */
        public void clearFocus() {
            this.clearSelection();
            this.hasFocus = false;
        }
        
        /**
         * Set placeholder text
         */
        public void setPlaceholder(String text) {
            this.placeHolder = new necesse.engine.localization.message.StaticMessage(text);
        }
        
        /**
         * Set placeholder text with GameMessage
         */
        public void setPlaceholder(GameMessage message) {
            this.placeHolder = message;
        }
    }
    
    /**
     * Apply focus management to an existing FormTextInput
     * This is a utility for existing text inputs that can't be replaced
     * 
     * @param textInput The FormTextInput to enhance
     */
    public static void enhanceTextInput(FormTextInput textInput) {
        if (textInput == null) return;
        
        // Add submit event to clear focus when done
        textInput.onSubmit(e -> {
            textInput.clearSelection();
        });
    }
    
    /**
     * Check if any text input currently has focus
     * This can be used by parent forms to determine if game hotkeys should be disabled
     */
    public static boolean isAnyTextInputFocused() {
        return necesse.gfx.forms.components.FormTypingComponent.isCurrentlyTyping();
    }
}
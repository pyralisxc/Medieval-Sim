package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.ui.ButtonColor;
import necesse.engine.localization.Language;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.StaticMessage;

/**
 * Language dropdown widget for LANGUAGE parameters.
 * Uses FormDropdownSelectionButton to select from available languages.
 */
public class LanguageDropdownWidget extends ParameterWidget {
    
    private FormDropdownSelectionButton<Language> languageDropdown;
    private Language[] availableLanguages;
    
    /**
     * Create a language dropdown widget.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     */
    public LanguageDropdownWidget(ParameterMetadata parameter, int x, int y) {
        super(parameter);
        
        // Get available languages from Necesse
        this.availableLanguages = Localization.getLanguages();
        
        // Create dropdown
        this.languageDropdown = new FormDropdownSelectionButton<Language>(
            x, y,
            FormInputSize.SIZE_32,
            ButtonColor.BASE,
            200, // width
            new StaticMessage("Select language...")
        );
        
        // Add all languages
        for (Language lang : availableLanguages) {
            languageDropdown.options.add(lang, new StaticMessage(lang.localDisplayName));
        }
        
        // Listen for selection changes
        languageDropdown.onSelected(event -> {
            currentValue = event.value != null ? event.value.stringID : null;
        });
    }
    
    @Override
    public String getValue() {
        Language selected = languageDropdown.getSelected();
        return selected != null ? selected.stringID : null;
    }
    
    @Override
    public void setValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            currentValue = null;
            return;
        }
        
        // Find language by string ID
        for (Language lang : availableLanguages) {
            if (lang.stringID.equalsIgnoreCase(value.trim())) {
                languageDropdown.setSelected(lang, new StaticMessage(lang.localDisplayName));
                currentValue = value.trim();
                return;
            }
        }
        
        // Language not found
        currentValue = null;
    }
    
    @Override
    public boolean validateValue() {
        String value = getValue();
        
        // Required params must have a value
        if (parameter.isRequired() && (value == null || value.trim().isEmpty())) {
            validationError = "Please select a language";
            return false;
        }
        
        validationError = null;
        return true;
    }
    
    @Override
    public necesse.gfx.forms.components.FormComponent getComponent() {
        return languageDropdown;
    }
}

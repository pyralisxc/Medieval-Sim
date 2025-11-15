package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import necesse.engine.localization.message.StaticMessage;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.ui.ButtonColor;

import java.util.List;

/**
 * Dropdown widget backed by Necesse's command autocomplete system.
 *
 * This is used for string-like parameters where the engine can provide
 * a finite set of valid options via CmdParameter.autoComplete.
 */
public class AutocompleteDropdownWidget extends ParameterWidget {

    private final FormDropdownSelectionButton<String> dropdown;
    private final String[] options;

    /**
     * Create an autocomplete-backed dropdown.
     *
     * @param parameter Parameter metadata
     * @param x         X position
     * @param y         Y position
     * @param optionList Options collected from CmdParameter.autoComplete
     */
    public AutocompleteDropdownWidget(ParameterMetadata parameter,
                                      int x,
                                      int y,
                                      List<String> optionList) {
        super(parameter);

        // Snapshot options into array for validation and reset logic
        if (optionList != null && !optionList.isEmpty()) {
            this.options = optionList.toArray(new String[0]);
        } else {
            this.options = new String[0];
        }

        this.dropdown = new FormDropdownSelectionButton<>(
            x,
            y,
            FormInputSize.SIZE_16,
            ButtonColor.BASE,
            200,
            new StaticMessage("Select " + parameter.getDisplayName())
        );

        // Populate dropdown options
        for (String option : this.options) {
            if (option == null || option.isEmpty()) {
                continue;
            }
            dropdown.options.add(option, new StaticMessage(option));
        }

        // Required params default to the first option; optional start empty
        if (parameter.isRequired() && this.options.length > 0) {
            String first = this.options[0];
            dropdown.setSelected(first, new StaticMessage(first));
            currentValue = first;
        } else {
            currentValue = null;
        }

        dropdown.onSelected(event -> {
            currentValue = event.value;
            // Notify CommandCenterPanel so it can refresh preview and buttons
            notifyValueChanged();
        });
    }

    @Override
    public String getValue() {
        String selected = dropdown.getSelected();
        if (selected == null && !parameter.isRequired()) {
            return null;
        }
        return selected;
    }

    @Override
    public void setValue(String value) {
        if (value == null) {
            if (!parameter.isRequired()) {
                dropdown.setSelected(null, new StaticMessage(""));
                currentValue = null;
            }
        } else {
            dropdown.setSelected(value, new StaticMessage(value));
            currentValue = value;
        }
    }

    @Override
    protected boolean validateValue() {
        String selected = dropdown.getSelected();

        if (parameter.isRequired() && selected == null) {
            validationError = "Please select a value";
            return false;
        }

        if (selected != null) {
            boolean found = false;
            for (String option : options) {
                if (option.equals(selected)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                validationError = "Invalid selection";
                return false;
            }
        }

        return true;
    }

    @Override
    public void reset() {
        if (parameter.isRequired() && options.length > 0) {
            String first = options[0];
            dropdown.setSelected(first, new StaticMessage(first));
            currentValue = first;
        } else {
            dropdown.setSelected(null, new StaticMessage(""));
            currentValue = null;
        }
        isValid = parameter.isOptional();
        validationError = null;
    }

    @Override
    public FormDropdownSelectionButton<String> getComponent() {
        return dropdown;
    }

    @Override
    public void onBlur() {
        // Validate when focus leaves the dropdown
        validate();
    }
}


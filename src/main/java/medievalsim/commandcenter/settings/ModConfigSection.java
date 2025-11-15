package medievalsim.commandcenter.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a section within a ModConfig class (e.g., BuildMode, Zones, etc.)
 * Each section contains multiple configurable settings.
 */
public class ModConfigSection {
    private final String name;
    private final String description;
    private final List<ConfigurableSetting> settings;
    
    public ModConfigSection(String name, String description) {
        this.name = name;
        this.description = description;
        this.settings = new ArrayList<>();
    }
    
    public ModConfigSection(String name, String description, List<ConfigurableSetting> settings) {
        this.name = name;
        this.description = description;
        this.settings = new ArrayList<>(settings);
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public List<ConfigurableSetting> getSettings() {
        return settings;
    }
    
    public void addSetting(ConfigurableSetting setting) {
        settings.add(setting);
    }
    
    public boolean isEmpty() {
        return settings.isEmpty();
    }
    
    public int getSettingCount() {
        return settings.size();
    }
    
    @Override
    public String toString() {
        return "ModConfigSection{name='" + name + "', settings=" + settings.size() + "}";
    }
}


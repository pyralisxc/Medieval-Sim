/*
 * Guild Research Container Form for Medieval Sim Mod
 * UI form for viewing and managing guild research through the Mage NPC.
 */
package medievalsim.guilds.research;

import necesse.engine.network.client.Client;
import necesse.gfx.GameColor;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.localComponents.FormLocalLabel;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.containerComponent.ContainerFormSwitcher;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.awt.Color;
import java.util.Collection;
import java.util.Set;

/**
 * Form for viewing and managing guild research.
 * Shows available projects, progress, and completed research.
 */
public class GuildResearchContainerForm extends ContainerFormSwitcher<GuildResearchContainer> {

    private static final int WIDTH = 450;
    private static final int HEIGHT = 400;

    private final Form mainForm;
    private final Form projectListForm;
    private final Form completedForm;

    public GuildResearchContainerForm(Client client, GuildResearchContainer container) {
        super(client, container);

        // === Main Form (Overview) ===
        this.mainForm = addComponent(new Form("main", WIDTH, HEIGHT));
        setupMainForm();

        // === Project List Form ===
        this.projectListForm = addComponent(new Form("projects", WIDTH, HEIGHT));
        setupProjectListForm();

        // === Completed Research Form ===
        this.completedForm = addComponent(new Form("completed", WIDTH, HEIGHT));
        setupCompletedForm();

        // Start on main form
        makeCurrent(mainForm);
    }
    
    /**
     * Request fresh data from the server.
     */
    private void requestRefresh() {
        container.requestRefreshAction.runAndSend();
    }
    
    @Override
    public boolean shouldOpenInventory() {
        return false; // Don't show player inventory
    }
    
    // Get state from container (which receives data from server)
    private String getActiveResearchID() {
        return container.getActiveResearchID();
    }
    
    private long getResearchProgress() {
        return container.getResearchProgress();
    }
    
    private Set<String> getCompletedResearch() {
        return container.getCompletedResearch();
    }
    
    private long getResearchPoints() {
        return container.getResearchPoints();
    }

    private void setupMainForm() {
        FormFlow flow = new FormFlow(10);

        // Title
        mainForm.addComponent(new FormLocalLabel("ui", "guildresearchtitle",
            new FontOptions(24), -1, WIDTH / 2, flow.next(35)));

        // Guild Research Points
        FormLabel pointsLabel = mainForm.addComponent(new FormLabel(
            "Research Points: " + getResearchPoints(),
            new FontOptions(16), -1, WIDTH / 2, flow.next(25)));
        pointsLabel.setColor(new Color(100, 200, 255));

        // Active Research Section
        mainForm.addComponent(new FormLocalLabel("ui", "guildresearchactive",
            new FontOptions(18), -1, 20, flow.next(30)));

        FormLabel activeLabel = mainForm.addComponent(new FormLabel(
            getActiveResearchDisplayText(),
            new FontOptions(14), -1, 20, flow.next(25)));
        activeLabel.setColor(new Color(200, 200, 200));

        // Progress bar placeholder (simple text for now)
        FormLabel progressLabel = mainForm.addComponent(new FormLabel(
            getProgressDisplayText(),
            new FontOptions(14), -1, 20, flow.next(20)));
        progressLabel.setColor(new Color(150, 255, 150));

        flow.next(20); // Spacing

        // Action Buttons
        int buttonWidth = 140;
        int buttonSpacing = 10;
        int startX = (WIDTH - (buttonWidth * 3 + buttonSpacing * 2)) / 2;

        // Browse Projects Button
        FormLocalTextButton browseBtn = mainForm.addComponent(
            new FormLocalTextButton("ui", "guildresearchbrowse",
                startX, flow.next(35), buttonWidth, FormInputSize.SIZE_24, ButtonColor.BASE));
        browseBtn.onClicked(e -> makeCurrent(projectListForm));

        // View Completed Button
        FormLocalTextButton completedBtn = mainForm.addComponent(
            new FormLocalTextButton("ui", "guildresearchcompleted",
                startX + buttonWidth + buttonSpacing, browseBtn.getY(), buttonWidth,
                FormInputSize.SIZE_24, ButtonColor.BASE));
        completedBtn.onClicked(e -> makeCurrent(completedForm));

        // Cancel Research Button (if active and has permission)
        if (container.canModifyResearch()) {
            FormLocalTextButton cancelBtn = mainForm.addComponent(
                new FormLocalTextButton("ui", "guildresearchcancel",
                    startX + (buttonWidth + buttonSpacing) * 2, browseBtn.getY(), buttonWidth,
                    FormInputSize.SIZE_24, ButtonColor.RED));
            cancelBtn.onClicked(e -> {
                if (getActiveResearchID() != null) {
                    container.cancelResearchAction.runAndSend();
                    // Request refresh after cancel
                    requestRefresh();
                }
            });
        }

        flow.next(50); // Spacing before close

        // Close Button
        FormLocalTextButton closeBtn = mainForm.addComponent(
            new FormLocalTextButton("ui", "backbutton",
                WIDTH / 2 - 50, HEIGHT - 40, 100, FormInputSize.SIZE_24, ButtonColor.BASE));
        closeBtn.onClicked(e -> client.closeContainer(true));

        mainForm.setHeight(HEIGHT);
    }

    private void setupProjectListForm() {
        FormFlow flow = new FormFlow(10);

        // Title
        projectListForm.addComponent(new FormLocalLabel("ui", "guildresearchprojects",
            new FontOptions(24), -1, WIDTH / 2, flow.next(35)));

        // Back button at top
        FormLocalTextButton backBtn = projectListForm.addComponent(
            new FormLocalTextButton("ui", "backbutton",
                WIDTH - 110, 10, 100, FormInputSize.SIZE_20, ButtonColor.BASE));
        backBtn.onClicked(e -> makeCurrent(mainForm));

        flow.next(10); // Spacing

        // Project list
        Collection<ResearchProject> allProjects = ResearchRegistry.getAllProjects();
        int projectIndex = 0;
        Set<String> completed = getCompletedResearch();
        String activeId = getActiveResearchID();

        for (ResearchProject project : allProjects) {
            boolean isCompleted = completed.contains(project.getId());
            boolean available = !isCompleted && project.hasPrerequisites(completed);
            boolean isActive = project.getId().equals(activeId);

            // Project name
            String displayName = project.getNameKey();
            String statusPrefix = isCompleted ? (GameColor.GREEN.getColorCode() + "✓ ") : 
                (isActive ? (GameColor.YELLOW.getColorCode() + "▶ ") : 
                (available ? (GameColor.GRAY.getColorCode() + "○ ") : (GameColor.DARK_GRAY.getColorCode() + "✗ ")));

            FormLabel nameLabel = projectListForm.addComponent(new FormLabel(
                statusPrefix + displayName,
                new FontOptions(14), -1, 20, flow.next(22)));

            // Cost and tier info
            String infoText = String.format("Tier %d | Cost: %d gold, %d points",
                project.getTier(), project.getCoinCost(), project.getResearchPointCost());
            FormLabel infoLabel = projectListForm.addComponent(new FormLabel(
                infoText, new FontOptions(12), -1, 40, flow.next(18)));
            infoLabel.setColor(new Color(150, 150, 150));

            // Start button (if available and has permission)
            if (available && container.canModifyResearch() && activeId == null) {
                final int idx = projectIndex;
                FormLocalTextButton startBtn = projectListForm.addComponent(
                    new FormLocalTextButton("ui", "guildresearchstart",
                        WIDTH - 120, nameLabel.getY() - 5, 100, FormInputSize.SIZE_20, ButtonColor.GREEN));
                startBtn.onClicked(e -> {
                    container.startResearchAction.runAndSend(idx);
                    // Request refresh after starting
                    requestRefresh();
                });
            }

            flow.next(5); // Spacing between projects
            projectIndex++;
        }

        projectListForm.setHeight(Math.max(HEIGHT, flow.next()));
    }

    private void setupCompletedForm() {
        FormFlow flow = new FormFlow(10);

        // Title
        completedForm.addComponent(new FormLocalLabel("ui", "guildresearchcompletedtitle",
            new FontOptions(24), -1, WIDTH / 2, flow.next(35)));

        // Back button at top
        FormLocalTextButton backBtn = completedForm.addComponent(
            new FormLocalTextButton("ui", "backbutton",
                WIDTH - 110, 10, 100, FormInputSize.SIZE_20, ButtonColor.BASE));
        backBtn.onClicked(e -> makeCurrent(mainForm));

        flow.next(10); // Spacing

        Set<String> completed = getCompletedResearch();
        if (completed.isEmpty()) {
            completedForm.addComponent(new FormLocalLabel("ui", "guildresearchnone",
                new FontOptions(16), -1, WIDTH / 2, flow.next(30)));
        } else {
            for (String projectId : completed) {
                ResearchProject project = ResearchRegistry.getProject(projectId);
                if (project != null) {
                    completedForm.addComponent(new FormLabel(
                        GameColor.GREEN.getColorCode() + "✓ " + project.getNameKey(),
                        new FontOptions(14), -1, 20, flow.next(20)));

                    // Show effect
                    if (project.getEffect() != null) {
                        String effectText = "  → " + project.getEffect().getDisplayString();
                        FormLabel effectLabel = completedForm.addComponent(new FormLabel(
                            effectText, new FontOptions(12), -1, 30, flow.next(18)));
                        effectLabel.setColor(new Color(100, 200, 100));
                    }
                }
            }
        }

        completedForm.setHeight(Math.max(HEIGHT, flow.next()));
    }

    private String getActiveResearchDisplayText() {
        String activeId = getActiveResearchID();
        if (activeId == null) {
            return "No active research";
        }
        ResearchProject project = ResearchRegistry.getProject(activeId);
        if (project == null) {
            return "Unknown research: " + activeId;
        }
        return project.getNameKey();
    }

    private String getProgressDisplayText() {
        String activeId = getActiveResearchID();
        if (activeId == null) {
            return "";
        }
        ResearchProject project = ResearchRegistry.getProject(activeId);
        if (project == null) {
            return "";
        }
        long progress = getResearchProgress();
        int percent = (int) (progress * 100 / Math.max(1, project.getResearchPointCost()));
        return String.format("Progress: %d / %d (%d%%)",
            progress, project.getResearchPointCost(), percent);
    }

    private void refreshMainForm() {
        // Rebuild the main form with updated data
        mainForm.clearComponents();
        setupMainForm();

        // Also rebuild project list if visible
        if (isCurrent(projectListForm)) {
            projectListForm.clearComponents();
            setupProjectListForm();
        }

        // Rebuild completed list if visible
        if (isCurrent(completedForm)) {
            completedForm.clearComponents();
            setupCompletedForm();
        }
    }
}

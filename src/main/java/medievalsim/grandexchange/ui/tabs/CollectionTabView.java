package medievalsim.grandexchange.ui.tabs;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import medievalsim.grandexchange.model.snapshot.CollectionEntrySnapshot;
import medievalsim.grandexchange.model.snapshot.CollectionPageSnapshot;
import medievalsim.grandexchange.net.GEFeedbackChannel;
import medievalsim.grandexchange.net.GEFeedbackLevel;
import medievalsim.grandexchange.ui.GrandExchangeContainer;
import medievalsim.grandexchange.ui.components.FeedbackLabelBinder;
import medievalsim.grandexchange.ui.components.TooltipFormCheckBox;
import medievalsim.grandexchange.ui.layout.GrandExchangeFonts;
import medievalsim.grandexchange.ui.util.GrandExchangeUIUtils;
import medievalsim.grandexchange.ui.viewmodel.GrandExchangeViewModel;
import medievalsim.grandexchange.ui.viewmodel.CollectionTabState;
import necesse.engine.localization.Localization;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.ui.ButtonColor;

/**
 * Dedicated builder for the Collection tab so the container only routes tabs.
 */
public final class CollectionTabView {
    private final TabHostContext host;
    private final GrandExchangeContainer container;
    private final GrandExchangeViewModel viewModel;
    private final BiConsumer<String, Boolean> feedbackChannel;
    private final Consumer<FormLabel> feedbackRegistrar;

    public CollectionTabView(TabHostContext host,
                             GrandExchangeContainer container,
                             GrandExchangeViewModel viewModel,
                             BiConsumer<String, Boolean> feedbackChannel,
                             Consumer<FormLabel> feedbackRegistrar) {
        this.host = host;
        this.container = container;
        this.viewModel = viewModel;
        this.feedbackChannel = feedbackChannel;
        this.feedbackRegistrar = feedbackRegistrar;
    }

    public void build(int startY) {
        CollectionTabState collectionState = viewModel.getCollectionTabState();
        CollectionPageSnapshot snapshot = viewModel.getCollectionSnapshot();
        if (snapshot == null) {
            snapshot = CollectionPageSnapshot.empty(container.playerAuth);
        }

        int currentY = startY;
        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.collection.header"), GrandExchangeFonts.HEADER, -1, 10, currentY));

        FormLabel feedback = new FormLabel("", GrandExchangeFonts.SMALL, -1, 10, currentY += 25);
        feedback.setColor(GrandExchangeFonts.SECONDARY_TEXT);
        host.addComponent(feedback);
        feedbackRegistrar.accept(feedback);
        host.addComponent(new FeedbackLabelBinder(
            feedback,
            () -> viewModel.getFeedbackBus().peek(GEFeedbackChannel.COLLECTION)));
        var preserved = viewModel.getFeedbackBus().peek(GEFeedbackChannel.COLLECTION);
        if (preserved != null) {
            feedback.setText(preserved.message());
            boolean isError = preserved.level() == GEFeedbackLevel.ERROR;
            feedback.setColor(isError ? GrandExchangeFonts.ERROR_TEXT : GrandExchangeFonts.SUCCESS_TEXT);
        }

        String pageInfo = Localization.translate("ui", "grandexchange.collection.pageinfo",
            "current", Integer.toString(collectionState.getDisplayPage()),
            "total", Integer.toString(Math.max(1, collectionState.getTotalPages())),
            "count", Integer.toString(collectionState.getTotalItems()));
        FormLabel pageLabel = new FormLabel(pageInfo, GrandExchangeFonts.SMALL, -1, 10, currentY += 20);
        pageLabel.setColor(GrandExchangeFonts.SECONDARY_TEXT);
        host.addComponent(pageLabel);

        int controlsY = currentY + 25;
        buildPaginationControls(collectionState, controlsY);

        currentY = controlsY + 40;
        currentY = buildCollectionEntries(collectionState, snapshot.entries(), currentY);

        int settingsStartY = buildDepositPreferenceRow(collectionState, currentY);
        buildSettingsSection(collectionState, settingsStartY);
    }

    private void buildPaginationControls(CollectionTabState state, int controlsY) {
        boolean canPrev = state.hasPreviousPage() && !state.isPageRequestPending();
        FormTextButton prevButton = host.addComponent(new FormTextButton(Localization.translate("ui", "grandexchange.collection.prevbutton"), 10, controlsY, 150, FormInputSize.SIZE_24, ButtonColor.BASE));
        prevButton.setActive(canPrev);
        prevButton.setTooltip(canPrev ? null : Localization.translate("ui", "grandexchange.collection.tooltip.prevdisabled"));
        prevButton.onClicked(e -> handleCollectionPageChange(state.getPageIndex() - 1, state));

        boolean canNext = state.hasNextPage() && !state.isPageRequestPending();
        FormTextButton nextButton = host.addComponent(new FormTextButton(Localization.translate("ui", "grandexchange.collection.nextbutton"), 180, controlsY, 150, FormInputSize.SIZE_24, ButtonColor.BASE));
        nextButton.setActive(canNext);
        nextButton.setTooltip(canNext ? null : Localization.translate("ui", "grandexchange.collection.tooltip.nextdisabled"));
        nextButton.onClicked(e -> handleCollectionPageChange(state.getPageIndex() + 1, state));
    }

    private int buildCollectionEntries(CollectionTabState state, List<CollectionEntrySnapshot> entries, int startY) {
        // Create scrollable area for collection entries
        int scrollAreaHeight = 280;
        int scrollAreaWidth = 860;
        FormContentBox entriesScrollArea = new FormContentBox(0, startY, scrollAreaWidth, scrollAreaHeight);
        entriesScrollArea.shouldLimitDrawArea = true;
        host.addComponent(entriesScrollArea);
        
        if (entries == null || entries.isEmpty()) {
            FormLabel emptyLabel = new FormLabel(Localization.translate("ui", "grandexchange.collection.empty"), GrandExchangeFonts.SMALL, -1, 10, 5);
            emptyLabel.setColor(GrandExchangeFonts.SECONDARY_TEXT);
            entriesScrollArea.addComponent(emptyLabel);
            return startY + scrollAreaHeight + 10;
        }

        int scrollY = 5;
        for (int i = 0; i < entries.size(); i++) {
            CollectionEntrySnapshot entry = entries.get(i);
            int blockTop = scrollY;
            FormLabel entryLabel = new FormLabel(Localization.translate("ui", "grandexchange.collection.entry.line",
                "item", getItemDisplayName(entry.itemStringID()),
                "quantity", Integer.toString(entry.quantity())), GrandExchangeFonts.BODY, -1, 10, blockTop);
            entriesScrollArea.addComponent(entryLabel);

            String metaLine = Localization.translate("ui", "grandexchange.collection.entry.meta",
                "source", Localization.translate("ui", "grandexchange.collection.source." + mapCollectionSourceKey(entry.source())),
                "age", GrandExchangeUIUtils.formatRelativeTime(entry.timestamp()));
            FormLabel metaLabel = new FormLabel(metaLine, GrandExchangeFonts.SMALL, -1, 10, blockTop + 18);
            metaLabel.setColor(GrandExchangeFonts.SECONDARY_TEXT);
            entriesScrollArea.addComponent(metaLabel);

            FormTextButton collectBtn = new FormTextButton(Localization.translate("ui", "grandexchange.collection.collectbutton"), 650, blockTop - 2, 200, FormInputSize.SIZE_24, ButtonColor.BASE);
            boolean slotPending = state.isCollectPending(i);
            boolean globalPending = state.hasCollectActionPending();
            boolean collectActive = !slotPending && !globalPending && !state.isPageRequestPending();
            collectBtn.setActive(collectActive);
            collectBtn.setTooltip(collectActive ? null : Localization.translate("ui", "grandexchange.collection.collecttooltip.pending"));
            int localIndex = i;
            collectBtn.onClicked(e -> handleCollectSingle(localIndex, state, collectBtn));
            entriesScrollArea.addComponent(collectBtn);

            scrollY = blockTop + 40;
        }
        entriesScrollArea.fitContentBoxToComponents(5);

        int currentY = startY + scrollAreaHeight + 10;
        FormTextButton collectAllBtn = new FormTextButton(Localization.translate("ui", "grandexchange.collection.collectall"), 10, currentY, 220, FormInputSize.SIZE_32, ButtonColor.GREEN);
        boolean hasItems = !entries.isEmpty();
        boolean collectAllActive = hasItems && !state.hasCollectActionPending() && !state.isPageRequestPending();
        collectAllBtn.setActive(collectAllActive);
        if (!hasItems) {
            collectAllBtn.setTooltip(Localization.translate("ui", "grandexchange.collection.collectall.tooltip.empty"));
        } else if (!collectAllActive) {
            collectAllBtn.setTooltip(Localization.translate("ui", "grandexchange.collection.collectall.tooltip.pending"));
        }
        collectAllBtn.onClicked(e -> handleCollectAll(state, collectAllBtn));
        host.addComponent(collectAllBtn);
        return currentY + 50;
    }

    private int buildDepositPreferenceRow(CollectionTabState state, int startY) {
        TooltipFormCheckBox depositPreference = new TooltipFormCheckBox(
            Localization.translate("ui", "grandexchange.collection.depositpref.label"),
            10,
            startY,
            state.isDepositToBankPreferred());
        depositPreference.setTooltipSupplier(() -> Localization.translate("ui", "grandexchange.collection.depositpref.tooltip"));
        boolean pending = state.isDepositPreferencePending();
        depositPreference.setActive(!pending);
        if (pending) {
            depositPreference.setTooltipSupplier(() -> Localization.translate("ui", "grandexchange.collection.depositpref.pending"));
        }
        depositPreference.onClicked(e -> handleDepositPreferenceToggle(depositPreference, state));
        host.addComponent(depositPreference);
        return startY + 35;
    }

    private void buildSettingsSection(CollectionTabState state, int startY) {
        int currentY = startY;
        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.collection.settings.header"), GrandExchangeFonts.HEADER, -1, 10, currentY));

        TooltipFormCheckBox autoBankCheckbox = new TooltipFormCheckBox(
            Localization.translate("ui", "grandexchange.collection.settings.autobank"),
            10,
            currentY += 30,
            state.isAutoSendToBankEnabled());
        configureSettingToggle(autoBankCheckbox, state.isAutoBankPending());
        autoBankCheckbox.onClicked(e -> handleAutoBankToggle(autoBankCheckbox, state));
        host.addComponent(autoBankCheckbox);

        TooltipFormCheckBox notifyPartialCheckbox = new TooltipFormCheckBox(
            Localization.translate("ui", "grandexchange.collection.settings.notify"),
            10,
            currentY += 30,
            state.isNotifyPartialSalesEnabled());
        configureSettingToggle(notifyPartialCheckbox, state.isNotifyPartialPending());
        notifyPartialCheckbox.onClicked(e -> handleNotifyPartialToggle(notifyPartialCheckbox, state));
        host.addComponent(notifyPartialCheckbox);

        TooltipFormCheckBox playSoundCheckbox = new TooltipFormCheckBox(
            Localization.translate("ui", "grandexchange.collection.settings.sound"),
            10,
            currentY += 30,
            state.isPlaySoundOnSaleEnabled());
        configureSettingToggle(playSoundCheckbox, state.isPlaySoundPending());
        playSoundCheckbox.onClicked(e -> handlePlaySoundToggle(playSoundCheckbox, state));
        host.addComponent(playSoundCheckbox);
    }

    private void configureSettingToggle(TooltipFormCheckBox toggle, boolean pending) {
        if (pending) {
            toggle.setActive(false);
            toggle.setTooltipSupplier(() -> Localization.translate("ui", "grandexchange.collection.depositpref.pending"));
        } else {
            toggle.clearTooltipSupplier();
            toggle.setActive(true);
        }
    }

    private void handleCollectionPageChange(int targetPage, CollectionTabState state) {
        if (state == null) {
            return;
        }
        int maxPage = Math.max(1, state.getTotalPages());
        int boundedTarget = Math.max(0, Math.min(targetPage, maxPage - 1));
        if (boundedTarget == state.getPageIndex()) {
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.collection.feedback.requestedpage"), true);
            return;
        }
        if (state.isPageRequestPending()) {
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.collection.feedback.pendingpage"), true);
            return;
        }
        state.requestPageChange();
        container.setCollectionPage.runAndSend(boundedTarget);
        feedbackChannel.accept(Localization.translate("ui", "grandexchange.collection.feedback.loadingpage",
            "page", Integer.toString(boundedTarget + 1)), false);
    }

    private void handleCollectSingle(int localIndex, CollectionTabState state, FormTextButton button) {
        if (state.hasCollectActionPending()) {
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.collection.feedback.pendingcollect"), true);
            return;
        }
        state.markCollectPending(localIndex);
        button.setActive(false);
        button.setTooltip(Localization.translate("ui", "grandexchange.collection.collecttooltip.pending"));
        container.collectItem.runAndSend(localIndex);
        feedbackChannel.accept(Localization.translate("ui", "grandexchange.collection.feedback.collecting"), false);
    }

    private void handleCollectAll(CollectionTabState state, FormTextButton button) {
        if (state.hasCollectActionPending()) {
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.collection.feedback.pendingcollect"), true);
            return;
        }
        state.markCollectAllPending();
        button.setActive(false);
        button.setTooltip(Localization.translate("ui", "grandexchange.collection.collectall.tooltip.pending"));
        container.collectAllToBank.runAndSend();
        feedbackChannel.accept(Localization.translate("ui", "grandexchange.collection.feedback.collecting"), false);
    }

    private void handleDepositPreferenceToggle(TooltipFormCheckBox toggle, CollectionTabState state) {
        if (state.isDepositPreferencePending()) {
            toggle.checked = !toggle.checked;
            feedbackChannel.accept(Localization.translate("ui", "grandexchange.collection.depositpref.pending"), true);
            return;
        }
        state.requestDepositPreferenceToggle();
        toggle.setActive(false);
        toggle.setTooltipSupplier(() -> Localization.translate("ui", "grandexchange.collection.depositpref.pending"));
        container.toggleCollectionDepositPreference.runAndSend(toggle.checked);
        feedbackChannel.accept(Localization.translate("ui", "grandexchange.collection.feedback.depositpref"), false);
    }

    private void handleAutoBankToggle(TooltipFormCheckBox toggle, CollectionTabState state) {
        if (state.isAutoBankPending()) {
            toggle.checked = !toggle.checked;
            return;
        }
        state.requestAutoBankToggle();
        toggle.setActive(false);
        toggle.setTooltipSupplier(() -> Localization.translate("ui", "grandexchange.collection.depositpref.pending"));
        container.toggleAutoBank.runAndSend(toggle.checked);
    }

    private void handleNotifyPartialToggle(TooltipFormCheckBox toggle, CollectionTabState state) {
        if (state.isNotifyPartialPending()) {
            toggle.checked = !toggle.checked;
            return;
        }
        state.requestNotifyPartialToggle();
        toggle.setActive(false);
        toggle.setTooltipSupplier(() -> Localization.translate("ui", "grandexchange.collection.depositpref.pending"));
        container.toggleNotifyPartial.runAndSend(toggle.checked);
    }

    private void handlePlaySoundToggle(TooltipFormCheckBox toggle, CollectionTabState state) {
        if (state.isPlaySoundPending()) {
            toggle.checked = !toggle.checked;
            return;
        }
        state.requestPlaySoundToggle();
        toggle.setActive(false);
        toggle.setTooltipSupplier(() -> Localization.translate("ui", "grandexchange.collection.depositpref.pending"));
        container.togglePlaySound.runAndSend(toggle.checked);
    }

    private String mapCollectionSourceKey(String source) {
        if (source == null) {
            return "unknown";
        }
        return switch (source) {
            case "purchase" -> "purchase";
            case "expired_offer" -> "expired";
            case "cancelled_offer" -> "cancelled";
            case "partial_sale" -> "partial";
            default -> "unknown";
        };
    }

    private String getItemDisplayName(String itemStringID) {
        return GrandExchangeUIUtils.getItemDisplayName(itemStringID);
    }
}

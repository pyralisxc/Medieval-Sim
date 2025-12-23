package medievalsim.grandexchange.ui.tabs;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import medievalsim.grandexchange.model.snapshot.HistoryEntrySnapshot;
import medievalsim.grandexchange.ui.layout.GrandExchangeFonts;
import medievalsim.grandexchange.ui.util.GrandExchangeUIUtils;
import medievalsim.grandexchange.ui.viewmodel.GrandExchangeViewModel;
import medievalsim.grandexchange.ui.viewmodel.HistoryTabState;
import medievalsim.grandexchange.ui.viewmodel.HistoryTabState.HistoryFilter;
import necesse.engine.localization.Localization;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.ui.ButtonColor;

/**
 * Encapsulates the History tab layout and pagination logic so the container
 * only decides which view to build.
 */
public final class HistoryTabView {
    private final TabHostContext host;
    private final GrandExchangeViewModel viewModel;
    private final BiConsumer<String, Boolean> feedbackChannel;
    private final Consumer<FormLabel> feedbackRegistrar;
    private final Runnable rebuildCallback;

    public HistoryTabView(TabHostContext host,
                          GrandExchangeViewModel viewModel,
                          BiConsumer<String, Boolean> feedbackChannel,
                          Consumer<FormLabel> feedbackRegistrar,
                          Runnable rebuildCallback) {
        this.host = host;
        this.viewModel = viewModel;
        this.feedbackChannel = feedbackChannel;
        this.feedbackRegistrar = feedbackRegistrar;
        this.rebuildCallback = rebuildCallback;
    }

    public void build(int startY) {
        HistoryTabState historyState = viewModel.getHistoryTabState();
        List<HistoryEntrySnapshot> entries = historyState.getEntries();
        int currentY = startY;

        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.history.header"), GrandExchangeFonts.HEADER, -1, 10, currentY));

        FormLabel hint = new FormLabel(Localization.translate("ui", "grandexchange.history.hint"), GrandExchangeFonts.SMALL, -1, 10, currentY += 25);
        hint.setColor(GrandExchangeFonts.SECONDARY_TEXT);
        host.addComponent(hint);

        FormLabel feedback = new FormLabel("", GrandExchangeFonts.SMALL, -1, 10, currentY += 20);
        feedback.setColor(GrandExchangeFonts.SECONDARY_TEXT);
        host.addComponent(feedback);
        feedbackRegistrar.accept(feedback);
        feedbackChannel.accept(Localization.translate("ui", "grandexchange.history.filters.current",
            "filter", translateHistoryFilterLabel(historyState.getActiveFilter())), false);

        int filterRowY = currentY + 25;
        int filterX = 10;
        for (HistoryFilter filter : HistoryFilter.values()) {
            ButtonColor color = filter == historyState.getActiveFilter() ? ButtonColor.RED : ButtonColor.BASE;
            HistoryFilter targetFilter = filter;
            FormTextButton filterBtn = host.addComponent(new FormTextButton(translateHistoryFilterLabel(filter), filterX, filterRowY, 140, FormInputSize.SIZE_24, color));
            filterBtn.onClicked(e -> {
                historyState.setActiveFilter(targetFilter);
                rebuildCallback.run();
            });
            filterX += 150;
        }

        currentY = filterRowY + 40;
        buildStatsSection(historyState, currentY);
        currentY += 25 + (6 * 18) + 10; // stats header + six rows + spacing

        buildResultsSection(historyState, entries != null ? entries : Collections.emptyList(), currentY);
    }

    private void buildStatsSection(HistoryTabState historyState, int startY) {
        int currentY = startY;
        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.history.stats.header"), GrandExchangeFonts.BODY, -1, 10, currentY));
        currentY += 25;

        String[] stats = new String[] {
            Localization.translate("ui", "grandexchange.history.stats.itemsbought", "value", Integer.toString(historyState.getTotalItemsPurchased())),
            Localization.translate("ui", "grandexchange.history.stats.itemssold", "value", Integer.toString(historyState.getTotalItemsSold())),
            Localization.translate("ui", "grandexchange.history.stats.sellofferscreated", "value", Integer.toString(historyState.getTotalSellOffersCreated())),
            Localization.translate("ui", "grandexchange.history.stats.sellofferscompleted", "value", Integer.toString(historyState.getTotalSellOffersCompleted())),
            Localization.translate("ui", "grandexchange.history.stats.buyorderscreated", "value", Integer.toString(historyState.getTotalBuyOrdersCreated())),
            Localization.translate("ui", "grandexchange.history.stats.buyorderscompleted", "value", Integer.toString(historyState.getTotalBuyOrdersCompleted()))
        };

        for (String stat : stats) {
            host.addComponent(new FormLabel(stat, GrandExchangeFonts.SMALL, -1, 20, currentY));
            currentY += 18;
        }
    }

    private void buildResultsSection(HistoryTabState historyState, List<HistoryEntrySnapshot> snapshotEntries, int startY) {
        int currentY = startY;
        host.addComponent(new FormLabel(Localization.translate("ui", "grandexchange.history.results.header"), GrandExchangeFonts.BODY, -1, 10, currentY));
        currentY += 25;

        List<HistoryEntrySnapshot> filteredEntries = filterHistoryEntries(historyState.getActiveFilter(), snapshotEntries);
        historyState.clampPageIndex(filteredEntries.size());

        int totalEntries = filteredEntries.size();
        int fromIndex = historyState.getPageIndex() * historyState.getPageSize();
        int toIndex = Math.min(totalEntries, fromIndex + historyState.getPageSize());
        int totalPages = historyState.getTotalPages(totalEntries);

        String rangeText = totalEntries == 0 ? "0" : String.format("%d-%d", fromIndex + 1, toIndex);
        FormLabel pageInfoLabel = new FormLabel(Localization.translate("ui", "grandexchange.history.pageinfo",
            "current", Integer.toString(totalPages == 0 ? 0 : historyState.getPageIndex() + 1),
            "total", Integer.toString(Math.max(1, totalPages)),
            "range", rangeText), GrandExchangeFonts.SMALL, -1, 10, currentY);
        pageInfoLabel.setColor(GrandExchangeFonts.SECONDARY_TEXT);
        host.addComponent(pageInfoLabel);

        FormTextButton prevButton = host.addComponent(new FormTextButton(Localization.translate("ui", "grandexchange.history.prevbutton"), 400, currentY - 5, 120, FormInputSize.SIZE_24, ButtonColor.BASE));
        prevButton.setActive(historyState.hasPreviousPage());
        if (!historyState.hasPreviousPage()) {
            prevButton.setTooltip(Localization.translate("ui", "grandexchange.history.tooltip.begin"));
        }
        prevButton.onClicked(e -> {
            historyState.goToPreviousPage();
            rebuildCallback.run();
        });

        boolean hasNext = historyState.hasNextPage(totalEntries);
        FormTextButton nextButton = host.addComponent(new FormTextButton(Localization.translate("ui", "grandexchange.history.nextbutton"), 530, currentY - 5, 120, FormInputSize.SIZE_24, ButtonColor.BASE));
        nextButton.setActive(hasNext);
        if (!hasNext) {
            nextButton.setTooltip(Localization.translate("ui", "grandexchange.history.tooltip.end"));
        }
        nextButton.onClicked(e -> {
            historyState.goToNextPage(totalEntries);
            rebuildCallback.run();
        });

        currentY += 30;
        
        // Create scrollable area for history entries
        int scrollAreaHeight = 280;
        int scrollAreaWidth = 860;
        FormContentBox entriesScrollArea = new FormContentBox(0, currentY, scrollAreaWidth, scrollAreaHeight);
        entriesScrollArea.shouldLimitDrawArea = true;
        host.addComponent(entriesScrollArea);
        
        if (totalEntries == 0) {
            entriesScrollArea.addComponent(new FormLabel(Localization.translate("ui", resolveEmptyKey(historyState.getActiveFilter())), GrandExchangeFonts.SMALL, -1, 20, 5));
            return;
        }

        int scrollY = 5;
        for (int i = fromIndex; i < toIndex; i++) {
            HistoryEntrySnapshot entry = filteredEntries.get(i);
            int blockTop = scrollY;

            // Determine action label based on sale vs purchase
            String actionKey = entry.isSale()
                ? "grandexchange.history.entry.summary.sale"
                : "grandexchange.history.entry.summary.purchase";
            FormLabel summaryLabel = new FormLabel(Localization.translate("ui", actionKey,
                "item", resolveItemDisplayName(entry.itemStringID()),
                "quantity", Integer.toString(entry.quantityTraded()),
                "total", Integer.toString(entry.totalCoins())), GrandExchangeFonts.BODY, -1, 10, blockTop);
            entriesScrollArea.addComponent(summaryLabel);

            // Counterparty label (buyer for sales, seller for purchases)
            String counterpartyLabelKey = entry.isSale() 
                ? "grandexchange.history.entry.meta.sale"
                : "grandexchange.history.entry.meta.purchase";
            String counterparty = entry.counterpartyName() != null ? entry.counterpartyName() :
                Localization.translate("ui", "grandexchange.history.entry.unknownparty");
            FormLabel metaLabel = new FormLabel(Localization.translate("ui", counterpartyLabelKey,
                "price", Integer.toString(entry.pricePerItem()),
                "counterparty", counterparty), GrandExchangeFonts.SMALL, -1, 10, blockTop + 18);
            metaLabel.setColor(GrandExchangeFonts.SECONDARY_TEXT);
            entriesScrollArea.addComponent(metaLabel);

            String relativeTime = GrandExchangeUIUtils.formatRelativeTime(entry.timestamp());
            FormLabel relativeLabel = new FormLabel(relativeTime, GrandExchangeFonts.SMALL, -1, 400, blockTop);
            relativeLabel.setColor(GrandExchangeFonts.INFO_TEXT);
            entriesScrollArea.addComponent(relativeLabel);

            if (historyState.isEntryUnseen(entry.timestamp())) {
                FormLabel badge = new FormLabel(Localization.translate("ui", "grandexchange.history.badge.new"), GrandExchangeFonts.SMALL, -1, 520, blockTop);
                badge.setColor(GrandExchangeFonts.BADGE_TEXT);
                entriesScrollArea.addComponent(badge);
            }

            scrollY += 38;
        }
        entriesScrollArea.fitContentBoxToComponents(5);
    }    private List<HistoryEntrySnapshot> filterHistoryEntries(HistoryFilter filter, List<HistoryEntrySnapshot> entries) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        if (filter == null || filter == HistoryFilter.ALL) {
            return entries;
        }
        // Filter by isSale flag: SALES shows sales only, PURCHASES shows purchases only
        boolean showSales = (filter == HistoryFilter.SALES);
        return entries.stream()
            .filter(entry -> entry.isSale() == showSales)
            .toList();
    }

    private String resolveItemDisplayName(String itemStringID) {
        return GrandExchangeUIUtils.getItemDisplayName(itemStringID);
    }

    private String translateHistoryFilterLabel(HistoryFilter filter) {
        HistoryFilter resolved = filter != null ? filter : HistoryFilter.ALL;
        String key = switch (resolved) {
            case ALL -> "grandexchange.history.filters.all";
            case SALES -> "grandexchange.history.filters.sales";
            case PURCHASES -> "grandexchange.history.filters.purchases";
        };
        return Localization.translate("ui", key);
    }

    private String resolveEmptyKey(HistoryFilter filter) {
        return switch (filter != null ? filter : HistoryFilter.ALL) {
            case ALL -> "grandexchange.history.empty.all";
            case SALES -> "grandexchange.history.empty.sales";
            case PURCHASES -> "grandexchange.history.empty.purchases";
        };
    }
}

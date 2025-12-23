package medievalsim.grandexchange.ui.layout;

/**
 * Reusable layout helpers for the multi-column Grand Exchange interface.
 */
public final class GrandExchangeLayout {

    public static final int FORM_MARGIN = 12;
    public static final int COLUMN_GUTTER = 12;
    public static final int DEFAULT_SECTION_SPACING = 16;

    private GrandExchangeLayout() {
    }

    public static Columns threeColumns(int totalWidth) {
        return threeColumns(totalWidth, 1.0f, 1.0f, 1.0f);
    }

    public static Columns threeColumns(int totalWidth, float leftWeight, float centerWeight, float rightWeight) {
        int availableWidth = Math.max(0, totalWidth - (FORM_MARGIN * 2));
        int gutterSpace = COLUMN_GUTTER * 2;
        float weightSum = Math.max(0.1f, leftWeight + centerWeight + rightWeight);
        int usableWidth = availableWidth - gutterSpace;
        int leftWidth = Math.max(120, Math.round(usableWidth * (leftWeight / weightSum)));
        int centerWidth = Math.max(120, Math.round(usableWidth * (centerWeight / weightSum)));
        int rightWidth = Math.max(120, usableWidth - leftWidth - centerWidth);
        int leftX = FORM_MARGIN;
        int centerX = leftX + leftWidth + COLUMN_GUTTER;
        int rightX = centerX + centerWidth + COLUMN_GUTTER;
        return new Columns(leftX, centerX, rightX, leftWidth, centerWidth, rightWidth);
    }

    public static int sectionBottom(int currentY, int sectionHeight) {
        return currentY + sectionHeight + DEFAULT_SECTION_SPACING;
    }

    public static final class Columns {
        public final int leftX;
        public final int centerX;
        public final int rightX;
        public final int leftWidth;
        public final int centerWidth;
        public final int rightWidth;

        private Columns(int leftX, int centerX, int rightX, int leftWidth, int centerWidth, int rightWidth) {
            this.leftX = leftX;
            this.centerX = centerX;
            this.rightX = rightX;
            this.leftWidth = leftWidth;
            this.centerWidth = centerWidth;
            this.rightWidth = rightWidth;
        }
    }
}

package medievalsim.buildmode.util;

public class BlockCountCalculator {
    public static int calculateBlockCount(int selectedShape, boolean isHollow, int lineLength, int squareSize, int circleRadius, int spacing) {
        return ShapeCalculator.calculatePositions(0, 0, selectedShape, isHollow, 0, lineLength, squareSize, circleRadius, spacing, 1, 1).size();
    }

    public static int calculateBlockCount(int selectedShape, boolean isHollow, int lineLength, int squareSize, int circleRadius) {
        return BlockCountCalculator.calculateBlockCount(selectedShape, isHollow, lineLength, squareSize, circleRadius, 1);
    }
}


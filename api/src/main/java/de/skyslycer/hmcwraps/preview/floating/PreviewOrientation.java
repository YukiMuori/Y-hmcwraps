package de.skyslycer.hmcwraps.preview.floating;

/** Pure preview-orientation rules, kept separate from packet code for safe unit testing. */
public final class PreviewOrientation {

    private PreviewOrientation() {
    }

    public static boolean isVerticalItemDisplay(String materialName) {
        return materialName != null && materialName.endsWith("_SWORD");
    }
}

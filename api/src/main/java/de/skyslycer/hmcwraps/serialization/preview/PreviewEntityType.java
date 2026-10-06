package de.skyslycer.hmcwraps.serialization.preview;

/**
 * Client-side entity used by floating previews.
 */
public enum PreviewEntityType {

    /** The legacy invisible armor stand with the item in its head slot. */
    ARMOR_STAND,
    /** A vanilla item display entity. */
    ITEM_DISPLAY,
    /** A mannequin with armor in its armor slot or other items in its main hand. */
    MANNEQUIN,
    /** Mannequin for armor when supported, item display for everything else. */
    AUTO

}

package de.skyslycer.hmcwraps.serialization.preview;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class PreviewSettings {

    private PreviewType type;
    private PreviewEntityType entityType;
    private int duration;
    private int rotation;
    private SneakCancel sneakCancel;
    private Bobbing bobbing;
    private ItemDisplayTransform itemDisplayTransform = new ItemDisplayTransform();

    public PreviewType getType() {
        return type;
    }

    public PreviewEntityType getEntityType() {
        // Item displays retain modern data components such as minecraft:item_model. AUTO keeps
        // mannequins for wearable armor and uses an item display for every other modern item.
        return entityType == null ? PreviewEntityType.AUTO : entityType;
    }

    public int getDuration() {
        return duration;
    }

    public int getRotation() {
        return rotation;
    }

    public SneakCancel getSneakCancel() {
        return sneakCancel;
    }

    public Bobbing getBobbing() {
        return bobbing;
    }

    public ItemDisplayTransform getItemDisplayTransform() {
        return itemDisplayTransform == null ? new ItemDisplayTransform() : itemDisplayTransform;
    }

}

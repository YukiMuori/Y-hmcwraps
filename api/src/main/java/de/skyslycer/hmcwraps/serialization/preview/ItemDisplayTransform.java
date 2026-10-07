package de.skyslycer.hmcwraps.serialization.preview;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class ItemDisplayTransform {

    private TransformVector translation = new TransformVector(0.0, 2.0, 0.0);
    private TransformVector itemRotation = new TransformVector(0.0, 0.0, 0.0);
    private TransformVector swordRotation = new TransformVector(90.0, 0.0, -90.0);

    public TransformVector getTranslation() {
        return translation == null ? new TransformVector(0.0, 2.0, 0.0) : translation;
    }

    public TransformVector getItemRotation() {
        return itemRotation == null ? new TransformVector() : itemRotation;
    }

    public TransformVector getSwordRotation() {
        return swordRotation == null ? new TransformVector(90.0, 0.0, -90.0) : swordRotation;
    }
}

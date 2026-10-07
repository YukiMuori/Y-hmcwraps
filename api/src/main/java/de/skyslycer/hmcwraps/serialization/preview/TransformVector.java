package de.skyslycer.hmcwraps.serialization.preview;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class TransformVector {

    private double x;
    private double y;
    private double z;

    public TransformVector() {
    }

    public TransformVector(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public boolean isZero() {
        return x == 0.0 && y == 0.0 && z == 0.0;
    }
}

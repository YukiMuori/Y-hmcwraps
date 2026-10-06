package de.skyslycer.hmcwraps.serialization.debug;

public class DebugPlayer implements Debuggable {

    private final boolean filter;
    private final DebugItemData wrapInHand;

    public DebugPlayer(boolean filter, DebugItemData wrapInHand) {
        this.filter = filter;
        this.wrapInHand = wrapInHand;
    }
}

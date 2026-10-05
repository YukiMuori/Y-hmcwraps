package de.skyslycer.hmcwraps.skin;

import org.jetbrains.annotations.Nullable;

/** The player's current relationship with a configured skin. */
public record SkinAccess(State state, @Nullable String requirement) {
    public enum State { FREE, OWNED, LOCKED, PURCHASABLE, PERMISSION }
}

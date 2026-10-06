package de.skyslycer.hmcwraps.shop;

/** The lifecycle of a gift. */
public enum GiftStatus {
    /** The gift was paid for but its ownership grant could not be confirmed yet. */
    PENDING("pending"),
    /** The recipient owns the gifted object; the notification may still be pending. */
    DELIVERED("delivered"),
    /** The gift failed and the sender was refunded. */
    FAILED("failed");

    private final String id;

    GiftStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static GiftStatus fromId(String input) {
        if (input == null) {
            return PENDING;
        }
        String normalized = input.toLowerCase(java.util.Locale.ROOT).trim();
        for (GiftStatus status : values()) {
            if (status.id.equals(normalized) || status.name().toLowerCase(java.util.Locale.ROOT).equals(normalized)) {
                return status;
            }
        }
        return PENDING;
    }
}

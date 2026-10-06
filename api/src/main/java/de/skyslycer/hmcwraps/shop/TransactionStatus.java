package de.skyslycer.hmcwraps.shop;

/** The result of a transactional purchase. */
public enum TransactionStatus {
    /** The journal row exists but the transaction did not finish; used for crash recovery. */
    PENDING,
    /** Everything was charged and granted exactly once. */
    SUCCESS,
    /** The player already owned everything in the request; nothing was charged. */
    ALREADY_OWNED,
    /** Another transaction for the same player and target is still running. */
    BUSY,
    /** The player cannot pay for the order. */
    INSUFFICIENT_FUNDS,
    /** The economy provider refused the withdrawal. */
    PAYMENT_FAILED,
    /** The economy provider or its currency is unavailable; nothing was charged. */
    PROVIDER_UNAVAILABLE,
    /** Ownership could not be persisted; the payment was refunded (or flagged for an administrator). */
    STORAGE_FAILED,
    /** The requested skin/bundle is unknown, disabled or outside its availability window. */
    UNAVAILABLE,
    /** A coupon was supplied but is not usable for this order. */
    COUPON_REJECTED,
    /** The player is not permitted to buy this object. */
    PERMISSION_DENIED,
    /** The request was invalid (unknown target, empty grant list, ...). */
    INVALID,
    /** Another plugin cancelled the purchase event; nothing was charged. */
    CANCELLED,
    /** An unexpected error occurred; the transaction was rolled back when possible. */
    ERROR
}

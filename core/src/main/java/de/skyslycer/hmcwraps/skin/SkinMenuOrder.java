package de.skyslycer.hmcwraps.skin;

import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Shared deterministic catalog ordering rules, kept independent from Bukkit inventory state. */
public final class SkinMenuOrder {
    private SkinMenuOrder() { }

    public static <T> Comparator<T> byRarity(ToIntFunction<? super T> priority,
                                             Function<? super T, String> id,
                                             boolean descending) {
        Comparator<T> rarity = Comparator.comparingInt(priority);
        if (descending) rarity = rarity.reversed();
        return rarity.thenComparing(value -> normalize(id.apply(value)), String.CASE_INSENSITIVE_ORDER);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}

package de.skyslycer.hmcwraps.shop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure, deterministic calculation of shop rotations.
 *
 * <p>A rotation is derived from its <em>cycle key</em> (the day or week id) and a configured salt, so
 * restarting or reloading the server can never reroll the shop: the same cycle always produces the same
 * selection. The rotation boundary is computed from the configured zone and reset time, which makes the
 * countdown independent of the client and of the server's uptime.</p>
 */
public final class ShopRotation {

    private ShopRotation() {
    }

    /** The supported refresh intervals. */
    public enum Interval {
        DAILY,
        WEEKLY,
        NEVER;

        public static Interval fromId(String input) {
            if (input == null) {
                return DAILY;
            }
            return switch (input.toLowerCase(java.util.Locale.ROOT).trim()) {
                case "weekly", "week" -> WEEKLY;
                case "never", "none", "disabled" -> NEVER;
                default -> DAILY;
            };
        }
    }

    /** Parses a {@code HH:mm} reset time, falling back to midnight. */
    public static @NotNull LocalTime parseResetTime(String input) {
        if (input == null || input.isBlank()) {
            return LocalTime.MIDNIGHT;
        }
        try {
            String[] parts = input.trim().split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return LocalTime.MIDNIGHT;
            }
            return LocalTime.of(hour, minute);
        } catch (RuntimeException exception) {
            return LocalTime.MIDNIGHT;
        }
    }

    /** Resolves a configured zone, falling back to UTC so rotations stay reproducible. */
    public static @NotNull ZoneId parseZone(String input) {
        if (input == null || input.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(input.trim());
        } catch (RuntimeException exception) {
            return ZoneId.of("UTC");
        }
    }

    /** The cycle key of the rotation running at the supplied instant. */
    public static @NotNull String cycleKey(@NotNull Instant now, @NotNull ZoneId zone, @NotNull Interval interval,
                                           @NotNull LocalTime resetTime) {
        ZonedDateTime local = now.atZone(zone);
        ZonedDateTime boundary = cycleStart(local, interval, resetTime);
        return switch (interval) {
            case WEEKLY -> boundary.toLocalDate().getYear() + "-W" + String.format("%02d", isoWeek(boundary.toLocalDate()));
            case DAILY -> boundary.toLocalDate().toString();
            case NEVER -> "static";
        };
    }

    /** The instant at which the next rotation starts. */
    public static @NotNull Instant nextRefresh(@NotNull Instant now, @NotNull ZoneId zone, @NotNull Interval interval,
                                               @NotNull LocalTime resetTime) {
        ZonedDateTime local = now.atZone(zone);
        ZonedDateTime boundary = cycleStart(local, interval, resetTime);
        ZonedDateTime next = switch (interval) {
            case DAILY -> boundary.plusDays(1);
            case WEEKLY -> boundary.plusWeeks(1);
            case NEVER -> boundary.plusYears(100);
        };
        return next.toInstant();
    }

    private static ZonedDateTime cycleStart(ZonedDateTime local, Interval interval, LocalTime resetTime) {
        ZonedDateTime todayReset = local.toLocalDate().atTime(resetTime).atZone(local.getZone());
        ZonedDateTime start = local.isBefore(todayReset) ? todayReset.minusDays(1) : todayReset;
        if (interval == Interval.WEEKLY) {
            return start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        return start;
    }

    private static int isoWeek(LocalDate date) {
        return date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
    }

    /**
     * Deterministically selects entries from a pool.
     *
     * <p>The selection is a seeded shuffle, therefore the same cycle key and salt always produce the same
     * result on every server and after every restart. A pool smaller than the requested slot count yields
     * the whole pool.</p>
     *
     * @param pool  the candidate ids, in a stable order
     * @param seed  the rotation salt
     * @param cycle the cycle key
     * @param slots the number of entries to select
     */
    public static @NotNull List<String> rotate(@NotNull List<String> pool, @NotNull String seed, @NotNull String cycle,
                                               int slots) {
        List<String> candidates = new ArrayList<>(pool);
        candidates.removeIf(value -> value == null || value.isBlank());
        candidates.sort(String::compareTo);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Random random = new Random(stableSeed(seed + "|" + cycle));
        java.util.Collections.shuffle(candidates, random);
        int count = Math.min(Math.max(1, slots), candidates.size());
        List<String> selected = new ArrayList<>(candidates.subList(0, count));
        selected.sort(String::compareTo);
        return List.copyOf(selected);
    }

    /** A stable, platform independent seed derived from a string. */
    public static long stableSeed(@NotNull String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            long result = 0;
            for (int index = 0; index < 8; index++) {
                result = (result << 8) | (bytes[index] & 0xFFL);
            }
            return result;
        } catch (java.security.NoSuchAlgorithmException exception) {
            // SHA-256 is mandatory in every Java implementation; this branch only exists for safety.
            return value.hashCode();
        }
    }

    /** Formats a duration as the countdown shown in menus and placeholders ({@code 05h 32m 18s}). */
    public static @NotNull String formatCountdown(long seconds) {
        long remaining = Math.max(0, seconds);
        long hours = remaining / 3600;
        long minutes = (remaining % 3600) / 60;
        long secs = remaining % 60;
        return String.format("%02dh %02dm %02ds", hours, minutes, secs);
    }

    /** Whether an entry id is valid ({@code null} and blank ids are ignored). */
    public static boolean valid(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}

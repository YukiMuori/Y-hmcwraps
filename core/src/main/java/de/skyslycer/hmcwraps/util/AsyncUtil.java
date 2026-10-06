package de.skyslycer.hmcwraps.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Small helpers for the asynchronous, stage-based code paths used by the shop and storage layers. */
public final class AsyncUtil {

    private AsyncUtil() {
    }

    /** A stage that runs a supplier and converts any thrown error into a failed stage. */
    public static <T> CompletionStage<T> safe(@NotNull Supplier<CompletionStage<T>> supplier) {
        try {
            CompletionStage<T> result = supplier.get();
            return result == null
                    ? CompletableFuture.failedFuture(new IllegalStateException("Operation returned no completion stage"))
                    : result;
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    /** A completed stage holding a value. */
    public static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    /** Waits for every stage, collecting the results; failed elements are reported as {@code null}. */
    public static <T> CompletionStage<List<T>> allOf(@NotNull List<CompletionStage<T>> stages) {
        if (stages.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        CompletableFuture<?>[] futures = stages.stream()
                .map(stage -> stage.toCompletableFuture().exceptionally(error -> null))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).thenApply(ignored -> {
            List<T> results = new ArrayList<>(stages.size());
            for (CompletionStage<T> stage : stages) {
                try {
                    results.add(stage.toCompletableFuture().join());
                } catch (CompletionException exception) {
                    results.add(null);
                }
            }
            return results;
        });
    }

    /** The root cause of a (possibly wrapped) failure. */
    public static @NotNull Throwable rootCause(@NotNull Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /** A short, log friendly description of a failure. */
    public static @NotNull String describe(@NotNull Throwable throwable) {
        Throwable cause = rootCause(throwable);
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}

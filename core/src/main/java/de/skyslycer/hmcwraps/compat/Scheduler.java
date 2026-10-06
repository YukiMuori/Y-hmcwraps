package de.skyslycer.hmcwraps.compat;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Scheduling boundary. The plugin only talks to this interface, so the Folia/Bukkit specifics stay
 * inside {@link de.skyslycer.hmcwraps.compat.paper.PaperScheduler} and the rest of the plugin cannot
 * accidentally schedule Bukkit API work on the wrong thread.
 */
public interface Scheduler {

    /** Whether the server runs Folia (regionised threading). */
    boolean isFolia();

    /** Runs a task on the thread that owns the entity. */
    void runOnEntity(@NotNull Entity entity, @NotNull Runnable task);

    /** Runs a task on the thread that owns the entity after the given delay in ticks. */
    void runOnEntityLater(@NotNull Entity entity, @NotNull Runnable task, long delayTicks);

    /** Runs a task on the main/global thread. */
    void runGlobal(@NotNull Runnable task);

    /** Runs an asynchronous task. Never touch Bukkit API from here beyond thread safe reads. */
    void runAsync(@NotNull Runnable task);

    /**
     * Runs a repeating global task.
     *
     * @param task      the task body
     * @param delayTicks the initial delay in ticks
     * @param periodTicks the period in ticks
     * @return a handle that can be cancelled
     */
    @NotNull Cancellable runTimer(@NotNull Runnable task, long delayTicks, long periodTicks);

    /** A cancellable task handle. */
    interface Cancellable {
        void cancel();
    }
}

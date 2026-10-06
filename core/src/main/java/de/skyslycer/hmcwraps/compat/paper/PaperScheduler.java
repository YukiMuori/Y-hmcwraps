package de.skyslycer.hmcwraps.compat.paper;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import de.skyslycer.hmcwraps.compat.Scheduler;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Paper (and Folia) implementation of {@link Scheduler}. FoliaLib hides the actual scheduling
 * differences, so this class is the only place that knows about them.
 */
public final class PaperScheduler implements Scheduler {

    private final FoliaLib foliaLib;

    public PaperScheduler(@NotNull FoliaLib foliaLib) {
        this.foliaLib = foliaLib;
    }

    @Override
    public boolean isFolia() {
        return foliaLib.isFolia();
    }

    @Override
    public void runOnEntity(@NotNull Entity entity, @NotNull Runnable task) {
        foliaLib.getScheduler().runAtEntity(entity, ignored -> task.run());
    }

    @Override
    public void runOnEntityLater(@NotNull Entity entity, @NotNull Runnable task, long delayTicks) {
        foliaLib.getScheduler().runAtEntityLater(entity, ignored -> task.run(), delayTicks);
    }

    @Override
    public void runGlobal(@NotNull Runnable task) {
        foliaLib.getScheduler().runNextTick(ignored -> task.run());
    }

    @Override
    public void runAsync(@NotNull Runnable task) {
        foliaLib.getScheduler().runAsync(ignored -> task.run());
    }

    @Override
    public @NotNull Cancellable runTimer(@NotNull Runnable task, long delayTicks, long periodTicks) {
        WrappedTask wrapped = foliaLib.getScheduler().runTimer(task, delayTicks, periodTicks);
        return wrapped::cancel;
    }
}

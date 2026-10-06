package de.skyslycer.hmcwraps.economy;

import de.skyslycer.hmcwraps.compat.Scheduler;
import de.skyslycer.hmcwraps.skin.EconomyProvider;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Runs economy provider calls on the thread that owns the player.
 *
 * <p>Most economy plugins are not thread safe, and on Folia a player is only reachable from its region
 * thread. Every paid flow therefore marshals the provider calls through this dispatcher instead of calling
 * them from the database executor.</p>
 */
public final class EntityEconomyDispatcher implements PurchaseTransactionService.EconomyDispatcher {

    private final Scheduler scheduler;
    private final Entity entity;

    public EntityEconomyDispatcher(@NotNull Scheduler scheduler, @NotNull Entity entity) {
        this.scheduler = scheduler;
        this.entity = entity;
    }

    @Override
    public CompletionStage<Double> balance(java.util.UUID playerId, EconomyProvider provider, String currency) {
        return onEntity(scheduler, entity, () -> provider.balance(playerId, currency));
    }

    @Override
    public CompletionStage<Boolean> withdraw(java.util.UUID playerId, EconomyProvider provider, String currency, double amount) {
        return onEntity(scheduler, entity, () -> provider.withdraw(playerId, currency, amount));
    }

    @Override
    public CompletionStage<Boolean> deposit(java.util.UUID playerId, EconomyProvider provider, String currency, double amount) {
        return onEntity(scheduler, entity, () -> provider.deposit(playerId, currency, amount));
    }

    /**
     * Runs a stage producing operation on the entity's thread without blocking the caller.
     *
     * @param scheduler the scheduler to use
     * @param entity    the entity owning the target thread
     * @param operation the operation returning the stage to mirror
     */
    public static <T> CompletionStage<T> onEntity(@NotNull Scheduler scheduler, @NotNull Entity entity,
                                                  @NotNull Supplier<CompletionStage<T>> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        scheduler.runOnEntity(entity, () -> {
            if (!entity.isValid()) {
                result.completeExceptionally(new IllegalStateException("The player is no longer available"));
                return;
            }
            try {
                CompletionStage<T> stage = operation.get();
                if (stage == null) {
                    result.completeExceptionally(new IllegalStateException("Economy provider returned no operation"));
                    return;
                }
                stage.whenComplete((value, error) -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                    } else {
                        result.complete(value);
                    }
                });
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        });
        return result;
    }
}

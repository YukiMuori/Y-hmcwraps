package de.skyslycer.hmcwraps.economy;

import de.skyslycer.hmcwraps.skin.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Optional ExcellentEconomy adapter implemented reflectively so the plugin does not link the
 * optional API (whose current distribution has a newer Java baseline) into the core jar.
 */
public final class ExcellentEconomyProvider implements EconomyProvider {
    private static final String API_CLASS = "su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI";
    private volatile Object api;
    private volatile Plugin apiPlugin;

    @Override public String id() { return "excellent_economy"; }

    @Override public boolean isAvailable() {
        try { return getApi() != null; }
        catch (Throwable ignored) { return false; }
    }

    @Override public boolean supportsCurrency(String currency) {
        try {
            Object service = getApi();
            if (service == null) return false;
            try {
                Object result = service.getClass().getMethod("hasCurrency", String.class).invoke(service, currency);
                if (result instanceof Boolean value) return value;
            } catch (NoSuchMethodException ignored) { }
            Object optional = service.getClass().getMethod("currencyById", String.class).invoke(service, currency);
            return optional instanceof java.util.Optional<?> value && value.isPresent();
        } catch (Throwable ignored) { return false; }
    }

    @Override
    public CompletionStage<Double> balance(UUID playerId, String currency) {
        try {
            Object service = requireApi();
            Object result = invokeAsync(service, "getBalanceAsync", playerId, currency);
            return asFuture(result).thenApply(value -> value instanceof Number number ? number.doubleValue() : 0D);
        } catch (Throwable throwable) { return CompletableFuture.failedFuture(unwrap(throwable)); }
    }

    @Override
    public CompletionStage<Boolean> withdraw(UUID playerId, String currency, double amount) {
        try {
            Object service = requireApi();
            Object result;
            try {
                // Prefer atomic support when an installed ExcellentEconomy build exposes it.
                result = invokeAsync(service, "withdrawIfEnoughAsync", playerId, currency, amount);
            } catch (NoSuchMethodException ignored) {
                result = invokeAsync(service, "withdrawAsync", playerId, currency, amount);
            }
            return asFuture(result).thenApply(ExcellentEconomyProvider::successful);
        } catch (Throwable throwable) { return CompletableFuture.failedFuture(unwrap(throwable)); }
    }

    @Override
    public CompletionStage<Boolean> deposit(UUID playerId, String currency, double amount) {
        return operation("depositAsync", playerId, currency, amount);
    }

    private CompletionStage<Boolean> operation(String methodName, UUID playerId, String currency, double amount) {
        try {
            Object service = requireApi();
            Object result = invokeAsync(service, methodName, playerId, currency, amount);
            return asFuture(result).thenApply(ExcellentEconomyProvider::successful);
        } catch (Throwable throwable) { return CompletableFuture.failedFuture(unwrap(throwable)); }
    }

    private Object invokeAsync(Object service, String methodName, Object... args) throws Exception {
        Method method = null;
        if (args.length == 3 && (methodName.equals("withdrawAsync") || methodName.equals("withdrawIfEnoughAsync")
                || methodName.equals("depositAsync"))) {
            Object context = createSilentContext();
            if (context != null) {
                Object[] withContext = new Object[]{args[0], args[1], args[2], context};
                method = findMethod(service.getClass(), methodName, withContext);
                if (method != null) args = withContext;
            }
        }
        if (method == null) method = findMethod(service.getClass(), methodName, args);
        if (method == null) throw new NoSuchMethodException("ExcellentEconomy API method " + methodName + " was not found");
        return method.invoke(service, args);
    }

    private Object createSilentContext() {
        try {
            Plugin plugin = apiPlugin;
            if (plugin == null) return null;
            Class<?> type = Class.forName("su.nightexpress.excellenteconomy.api.currency.operation.OperationContext", true, plugin.getClass().getClassLoader());
            Object context = type.getMethod("custom", String.class).invoke(null, "Y-HMCWraps skin shop");
            return type.getMethod("silent").invoke(context);
        } catch (Throwable ignored) { return null; }
    }

    private Object getApi() throws Exception {
        Plugin economy = Bukkit.getPluginManager().getPlugin("ExcellentEconomy");
        if (economy == null || !economy.isEnabled()) {
            api = null;
            apiPlugin = null;
            return null;
        }
        Class<?> apiType = Class.forName(API_CLASS, true, economy.getClass().getClassLoader());
        @SuppressWarnings({"rawtypes", "unchecked"}) RegisteredServiceProvider<?> registration =
                Bukkit.getServicesManager().getRegistration((Class) apiType);
        if (registration == null) {
            api = null;
            apiPlugin = null;
            return null;
        }
        apiPlugin = economy;
        api = registration.getProvider();
        return api;
    }

    private Object requireApi() throws Exception {
        Object value = getApi();
        if (value == null) throw new IllegalStateException("ExcellentEconomy is not enabled or its API service is not registered");
        return value;
    }

    private static Method findMethod(Class<?> type, String name, Object[] args) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean matches = true;
            for (int index = 0; index < parameters.length; index++) {
                if (args[index] != null && !box(parameters[index]).isAssignableFrom(args[index].getClass())) { matches = false; break; }
            }
            if (matches) return method;
        }
        return null;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == double.class) return Double.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        return type;
    }

    private static CompletableFuture<?> asFuture(Object result) {
        if (result instanceof CompletionStage<?> stage) return stage.toCompletableFuture();
        return CompletableFuture.completedFuture(result);
    }

    private static boolean successful(Object result) {
        if (result instanceof Boolean value) return value;
        if (result == null) return false;
        try {
            Object value = result.getClass().getMethod("success").invoke(result);
            if (value instanceof Boolean success) return success;
        } catch (Throwable ignored) { }
        try {
            Object value = result.getClass().getMethod("bool").invoke(result);
            if (value instanceof Boolean success) return success;
        } catch (Throwable ignored) { }
        return "SUCCESS".equalsIgnoreCase(result.toString());
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof java.lang.reflect.InvocationTargetException invocation && invocation.getCause() != null) return invocation.getCause();
        return throwable;
    }
}

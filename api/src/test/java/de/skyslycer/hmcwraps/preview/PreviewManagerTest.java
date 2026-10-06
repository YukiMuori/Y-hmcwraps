package de.skyslycer.hmcwraps.preview;

import de.skyslycer.hmcwraps.HMCWraps;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression tests for the preview lifecycle: a preview whose {@code cancel} throws (for example
 * the NPE previously produced by {@code FloatingPreview.cancel} when its tasks were still null)
 * used to stay registered in the manager and make every later remove, create and reload throw.
 */
class PreviewManagerTest {

    @Test
    void removeCancelsThePreviewAndUnregistersIt() throws Exception {
        var manager = new PreviewManager(fakePlugin());
        var preview = new FakePreview(null);
        var uuid = UUID.randomUUID();
        register(manager, uuid, preview);

        manager.remove(uuid, true);

        assertEquals(List.of(true), preview.cancelCalls);
        assertFalse(isRegistered(manager, uuid));
    }

    @Test
    void removeOfUnknownUuidDoesNothing() {
        var manager = new PreviewManager(fakePlugin());

        assertDoesNotThrow(() -> manager.remove(UUID.randomUUID(), false));
    }

    @Test
    void throwingCancelNeverStrandsThePreviewInTheManager() throws Exception {
        var manager = new PreviewManager(fakePlugin());
        var preview = new FakePreview(new NullPointerException("cancel failed"));
        var uuid = UUID.randomUUID();
        register(manager, uuid, preview);

        assertDoesNotThrow(() -> manager.remove(uuid, false));
        assertEquals(List.of(false), preview.cancelCalls);
        assertFalse(isRegistered(manager, uuid));

        assertDoesNotThrow(() -> manager.remove(uuid, false));
        assertEquals(List.of(false), preview.cancelCalls, "a removed preview must not be cancelled twice");
    }

    @Test
    void removeAllCancelsEveryPreviewEvenWhenOneThrows() throws Exception {
        var manager = new PreviewManager(fakePlugin());
        var first = new FakePreview(null);
        var broken = new FakePreview(new NullPointerException("cancel failed"));
        var last = new FakePreview(null);
        register(manager, UUID.randomUUID(), first);
        var brokenUuid = UUID.randomUUID();
        register(manager, brokenUuid, broken);
        register(manager, UUID.randomUUID(), last);

        assertDoesNotThrow(() -> manager.removeAll(true));

        assertEquals(List.of(true), first.cancelCalls);
        assertEquals(List.of(true), broken.cancelCalls);
        assertEquals(List.of(true), last.cancelCalls);
        assertFalse(isRegistered(manager, brokenUuid));
    }

    private static void register(PreviewManager manager, UUID uuid, Preview preview) throws Exception {
        previewsOf(manager).put(uuid, preview);
    }

    private static boolean isRegistered(PreviewManager manager, UUID uuid) throws Exception {
        return previewsOf(manager).containsKey(uuid);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Preview> previewsOf(PreviewManager manager) throws Exception {
        var field = PreviewManager.class.getDeclaredField("previews");
        field.setAccessible(true);
        return (Map<UUID, Preview>) field.get(manager);
    }

    private static HMCWraps fakePlugin() {
        return (HMCWraps) Proxy.newProxyInstance(
                HMCWraps.class.getClassLoader(),
                new Class<?>[] { HMCWraps.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLogger" -> Logger.getLogger("PreviewManagerTest");
                    case "toString" -> "FakeHMCWraps";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static final class FakePreview implements Preview {

        private final RuntimeException cancelFailure;
        private final List<Boolean> cancelCalls = new ArrayList<>();

        private FakePreview(RuntimeException cancelFailure) {
            this.cancelFailure = cancelFailure;
        }

        @Override
        public void preview() {
        }

        @Override
        public void cancel(boolean open) {
            cancelCalls.add(open);
            if (cancelFailure != null) {
                throw cancelFailure;
            }
        }

    }

}

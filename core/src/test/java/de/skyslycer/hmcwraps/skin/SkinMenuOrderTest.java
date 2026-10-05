package de.skyslycer.hmcwraps.skin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkinMenuOrderTest {
    private record Entry(String id, int priority) { }

    @Test
    void defaultRarityOrderIsDescendingWithStableIdTies() {
        List<Entry> entries = List.of(
                new Entry("common_z", 1),
                new Entry("epic", 4),
                new Entry("common_a", 1),
                new Entry("legendary", 5));

        List<String> ordered = entries.stream()
                .sorted(SkinMenuOrder.byRarity(Entry::priority, Entry::id, true))
                .map(Entry::id)
                .toList();

        assertEquals(List.of("legendary", "epic", "common_a", "common_z"), ordered);
    }

    @Test
    void ascendingRarityOrderUsesAscendingPriorityAndStableIdTies() {
        List<Entry> entries = List.of(new Entry("b", 2), new Entry("c", 3), new Entry("a", 2));

        List<String> ordered = entries.stream()
                .sorted(SkinMenuOrder.byRarity(Entry::priority, Entry::id, false))
                .map(Entry::id)
                .toList();

        assertEquals(List.of("a", "b", "c"), ordered);
    }
}

package com.sakurakugu.fakeplayer.client.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkLoadMapControllerTest {
    @Test
    void nextRegionNameUsesFirstAvailableSuffixIgnoringCase() {
        assertEquals("region_3", ChunkLoadMapController.nextRegionName(
            List.of("region_1", "REGION_2", "main_base")));
    }
}

package com.sount.restful;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginResourceInspectionTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final Path RESOURCES = ROOT.resolve("src/main/resources");

    @Test
    public void svgGradientStopsDeclareInitialOffsets() throws Exception {
        List<String> svgFiles = List.of(
                "META-INF/pluginIcon.svg",
                "META-INF/pluginIcon_dark.svg",
                "icons/restService.svg",
                "icons/restService_dark.svg"
        );

        for (String svgFile : svgFiles) {
            String svg = Files.readString(RESOURCES.resolve(svgFile));
            assertFalse(svgFile + " has a gradient stop without offset",
                    svg.contains("<stop stop-color="));
            assertTrue(svgFile + " should declare the initial gradient stop",
                    svg.contains("<stop offset=\"0\""));
        }
    }
}

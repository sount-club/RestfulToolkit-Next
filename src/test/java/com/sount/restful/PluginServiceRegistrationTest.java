package com.sount.restful;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginServiceRegistrationTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final Path PLUGIN_XML = ROOT.resolve("src/main/resources/META-INF/plugin.xml");
    private static final Path SEARCH_HISTORY = ROOT.resolve(
            "src/main/java/com/sount/restful/search/application/SearchHistory.java");

    @Test
    public void annotatedProjectServicesAreNotAlsoRegisteredInPluginXml() throws Exception {
        String pluginXml = Files.readString(PLUGIN_XML);

        assertFalse(pluginXml.contains("serviceImplementation=\"com.sount.restful.search.application.SearchHistory\""));
        assertFalse(pluginXml.contains("serviceImplementation=\"com.sount.restful.search.SearchHistory\""));
    }

    @Test
    public void searchHistoryUsesProjectServiceAnnotation() throws Exception {
        String source = Files.readString(SEARCH_HISTORY);

        assertTrue(source.contains("@Service(Service.Level.PROJECT)"));
    }

    @Test
    public void endpointIndexIsRegisteredInPluginXml() throws Exception {
        String pluginXml = Files.readString(PLUGIN_XML);

        assertTrue(pluginXml.contains(
                "serviceImplementation=\"com.sount.restful.search.application.EndpointIndex\""));
        assertFalse(pluginXml.contains(
                "serviceImplementation=\"com.sount.restful.search.EndpointIndex\""));
    }
}

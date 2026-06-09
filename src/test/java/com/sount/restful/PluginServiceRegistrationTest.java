package com.sount.restful;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginServiceRegistrationTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final Path PLUGIN_XML = ROOT.resolve("src/main/resources/META-INF/plugin.xml");

    @Test
    public void projectServicesRetrievedFromProjectAreAnnotatedAsProjectServices() throws Exception {
        List<String> services = List.of(
                "src/main/java/com/sount/restful/navigator/RestServiceDetail.java",
                "src/main/java/com/sount/restful/navigator/RestServiceProjectsManager.java",
                "src/main/java/com/sount/restful/navigator/RestServicesNavigator.java"
        );

        for (String service : services) {
            String source = Files.readString(ROOT.resolve(service));
            assertTrue(service + " should import IntelliJ Service annotation",
                    source.contains("import com.intellij.openapi.components.Service;"));
            assertTrue(service + " should be a project-level service",
                    source.contains("@Service(Service.Level.PROJECT)"));
        }
    }

    @Test
    public void annotatedProjectServicesAreNotAlsoRegisteredInPluginXml() throws Exception {
        String pluginXml = Files.readString(PLUGIN_XML);

        assertFalse(pluginXml.contains("serviceImplementation=\"com.sount.restful.search.SearchHistory\""));
    }
}

package com.sount.restful;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;

public class ArchitectureBoundaryTest {

    private static final Path JAVA_ROOT = Path.of("").toAbsolutePath()
            .resolve("src/main/java/com/sount/restful");

    @Test
    public void pureDomainTypesDoNotDependOnIntellijPlatformApis() throws Exception {
        List<String> pureDomainFiles = List.of(
                "endpoint/model/EndpointDescriptor.java",
                "search/domain/SearchDocument.java",
                "search/domain/SearchResult.java",
                "search/domain/SearchEngine.java",
                "search/domain/SearchQuery.java",
                "search/domain/PathTemplateMatcher.java",
                "search/domain/PathSearchOptions.java",
                "method/HttpMethod.java",
                "method/RequestPath.java"
        );

        for (String relativePath : pureDomainFiles) {
            String source = Files.readString(JAVA_ROOT.resolve(relativePath));
            assertFalse(relativePath + " must stay independent from IntelliJ Platform APIs",
                    source.contains("import com.intellij."));
        }
    }

    @Test
    public void searchDomainDoesNotDependOnApplicationOrUiLayers() throws Exception {
        assertJavaSourcesDoNotContain("search/domain",
                List.of("import com.intellij.",
                        "import com.sount.restful.endpoint.navigation.",
                        "import com.sount.restful.search.application.",
                        "import com.sount.restful.search.ui."));
    }

    @Test
    public void searchApplicationDoesNotDependOnUiLayer() throws Exception {
        assertJavaSourcesDoNotContain("search/application",
                List.of("import com.sount.restful.search.ui."));
    }

    @Test
    public void endpointModelDoesNotDependOnPlatformOrHigherLayers() throws Exception {
        assertJavaSourcesDoNotContain("endpoint/model",
                List.of("import com.intellij.",
                        "import com.sount.restful.endpoint.navigation.",
                        "import com.sount.restful.endpoint.resolver.",
                        "import com.sount.restful.search."));
    }

    private static void assertJavaSourcesDoNotContain(String relativeDirectory,
                                                       List<String> forbiddenImports) throws Exception {
        Path directory = JAVA_ROOT.resolve(relativeDirectory);
        try (Stream<Path> sources = Files.walk(directory)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String content = Files.readString(source);
                for (String forbiddenImport : forbiddenImports) {
                    assertFalse(source + " must not contain " + forbiddenImport,
                            content.contains(forbiddenImport));
                }
            }
        }
    }
}

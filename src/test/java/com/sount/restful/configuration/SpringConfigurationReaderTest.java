package com.sount.restful.configuration;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Unit tests for Spring placeholder resolution in {@link SpringConfigurationReader}.
 * Covers the regex-based default-value extraction (B3 fix).
 */
public class SpringConfigurationReaderTest extends BasePlatformTestCase {

    public void testPlaceholderWithDefaultValueResolvesToDefault() {
        assertEquals("8080", newHandler().cleanPlaceholderIfExist("${port:8080}"));
    }

    public void testPlaceholderWithMultipleColonsKeepsValueAfterFirstColon() {
        // Spring treats everything after the first colon as the default value.
        assertEquals("b:c", newHandler().cleanPlaceholderIfExist("${a:b:c}"));
    }

    public void testPlaceholderWithoutDefaultValueIsLeftIntact() {
        // No inline default and no other property source to resolve against — leave as-is.
        assertEquals("${port}", newHandler().cleanPlaceholderIfExist("${port}"));
    }

    public void testMultiplePlaceholdersAllResolved() {
        assertEquals("12", newHandler().cleanPlaceholderIfExist("${a:1}${b:2}"));
    }

    public void testPlaceholderWithPrefixTextPreserved() {
        assertEquals("host=8080", newHandler().cleanPlaceholderIfExist("host=${port:8080}"));
    }

    public void testValueWithoutPlaceholderReturnedUnchanged() {
        assertEquals("8080", newHandler().cleanPlaceholderIfExist("8080"));
    }

    public void testNullReturnsNull() {
        assertNull(newHandler().cleanPlaceholderIfExist(null));
    }

    public void testServletContextPathTakesPrecedenceOverLegacyContextPath() {
        myFixture.configureByText("application.properties", """
                server.context-path=/legacy
                server.servlet.context-path=/current
                """);

        assertEquals("/current", newHandler().getContextPath());
    }

    public void testYamlLookupFallsBackWhenEarlierFileDoesNotContainProperty() {
        myFixture.configureByText("application.yml", "spring:\n  application:\n    name: demo\n");
        myFixture.configureByText("bootstrap.yml", "server:\n  port: 9090\n");

        assertEquals("9090", newHandler().getServerPort());
    }

    public void testPropertiesFormatKeepsPriorityOverYamlInSameConfigGroup() {
        myFixture.configureByText("application.properties", "server.port=8081\n");
        myFixture.configureByText("application.yml", "server:\n  port: 9091\n");

        assertEquals("8081", newHandler().getServerPort());
    }

    public void testActiveProfileOverridesDefaultConfiguration() {
        myFixture.configureByText("application.properties", """
                spring.profiles.active=dev
                server.port=8080
                """);
        myFixture.configureByText("application-dev.yml", "server:\n  port: 9090\n");

        assertEquals("9090", newHandler().getServerPort());
    }

    public void testMalformedYamlFallsBackToNextConfigFile() {
        myFixture.configureByText("application.yml", "server: [\n");
        myFixture.configureByText("bootstrap.properties", "server.port=7070\n");

        assertEquals("7070", newHandler().getServerPort());
    }

    private SpringConfigurationReader newHandler() {
        return new SpringConfigurationReader(getModule());
    }
}

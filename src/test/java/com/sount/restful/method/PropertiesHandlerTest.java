package com.sount.restful.method;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Unit tests for Spring placeholder resolution in {@link PropertiesHandler}.
 * Covers the regex-based default-value extraction (B3 fix).
 */
public class PropertiesHandlerTest extends BasePlatformTestCase {

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

    private PropertiesHandler newHandler() {
        return new PropertiesHandler(getModule());
    }
}

package com.sount.restful.method;

import junit.framework.TestCase;

public class ParameterTest extends TestCase {
    public void testShortTypeNameStripsPackageAndGenericArguments() {
        Parameter parameter = new Parameter("java.util.List<com.example.Store>", "stores");

        assertEquals("List", parameter.getShortTypeName());
    }

    public void testShortTypeNameKeepsArraySuffix() {
        Parameter parameter = new Parameter("java.lang.String[]", "names");

        assertEquals("String[]", parameter.getShortTypeName());
    }
}

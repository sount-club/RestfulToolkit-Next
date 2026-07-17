package com.sount.restful.configuration;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * 验证不同配置格式策略产出统一扁平键值模型。
 */
public class ConfigurationFileParserTest {

    @Test
    public void propertiesParserUsesPropertiesExtensionAndValues() {
        ConfigurationFileParser parser = new PropertiesConfigurationParser();

        assertEquals("properties", parser.extension());
        assertEquals("8080", parser.parse("server.port=8080\n").get("server.port"));
    }

    @Test
    public void yamlParserFlattensNestedMapsAndCollections() {
        ConfigurationFileParser parser = new YamlConfigurationParser();

        Map<String, Object> values = parser.parse("""
                server:
                  port: 9090
                routes:
                  - /users
                  - /orders
                """);

        assertEquals(9090, values.get("server.port"));
        assertEquals("/users", values.get("routes[0]"));
        assertEquals("/orders", values.get("routes[1]"));
    }
}

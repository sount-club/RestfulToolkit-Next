@file:Suppress("UnstableApiUsage")

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.10.5"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
}

group = "com.sount"
version = "1.1.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdea("2026.1.3")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:

        composeUI()

        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }

    // RSyntaxTextArea for syntax highlighting
    implementation("com.fifesoft:rsyntaxtextarea:3.5.1")
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }

        changeNotes = """
            <p><strong>RestfulToolkit Next 1.1.1</strong></p>
            <ul>
              <li>Updated README and plugin metadata to match the current unified search popup and editor context-menu features</li>
              <li>Fixed service registration tests for <code>EndpointIndex</code> and the project-level <code>SearchHistory</code> service</li>
              <li>Introduced <code>EndpointDescriptor</code> as a pure endpoint data model to reduce coupling between navigation, search fields, and PSI elements</li>
              <li>Extracted <code>SearchPopupModel</code> for non-UI search popup logic such as status text, recent results, and selection restoration</li>
              <li>Added <code>ServiceResolverRegistry</code> to centralize Spring and JAX-RS resolver registration for future framework extensions</li>
              <li>Split Spring Java and Kotlin endpoint discovery collaborators to keep <code>SpringResolver</code> focused on orchestration</li>
              <li>Added architecture guard tests and verified the project with <code>./gradlew test</code> and <code>./gradlew build</code></li>
            </ul>

            <p><strong>RestfulToolkit Next 1.1.0</strong></p>
            <ul>
              <li>Added a unified REST endpoint search popup with path, HTTP method, module, controller, method name, and description matching</li>
              <li>Supported controller-method queries such as <code>UserController#getUser</code> and <code>UserController getUser</code></li>
              <li>Preserved duplicate method/path endpoints from different controllers or modules instead of hiding them during search</li>
              <li>Improved startup behavior by showing indexing status and refreshing search results and module filters after endpoint indexing completes</li>
              <li>Added English and Chinese localization for the search popup, result list, preview panel, and status messages</li>
              <li>Improved search performance by caching selection keys, optimizing recent endpoint ranking, and reducing renderer allocations</li>
            </ul>

            <p><strong>RestfulToolkit Next 1.0.1</strong></p>
            <ul>
              <li>Grouped editor context-menu actions under the RestfulToolkit Next submenu</li>
              <li>Fixed method and class right-click actions so they resolve from the current caret/context element</li>
              <li>Improved endpoint navigation and editor context-menu actions for generating URLs and request data</li>
            </ul>
        """.trimIndent()
    }

    pluginVerification {
        ides {
            // 推荐方式：自动使用兼容当前插件设置的推荐版本
            recommended()
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

@file:Suppress("UnstableApiUsage")

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
}

group = "com.sount"
version = "1.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdea("2026.2")
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
            <p><strong>RestfulToolkit Next 1.2.0</strong></p>
            <ul>
              <li>Reorganized endpoint discovery, search, navigation, and configuration code into clearer domain, application, UI, and platform layers while preserving existing actions, shortcuts, and workflows</li>
              <li>Introduced Strategy, Template Method, Composite, and Facade patterns to make Spring MVC, WebFlux, and JAX-RS endpoint resolution easier to extend and maintain</li>
              <li>Reduced endpoint index rebuild overhead by reusing immutable module configuration snapshots and avoiding repeated configuration scans</li>
              <li>Optimized unified search with bounded top-result ranking and fewer hot-path allocations while preserving filters, history, scoring, and navigation behavior</li>
              <li>Added pluggable properties and YAML configuration parsers while preserving profile precedence, file precedence, placeholder resolution, and context-path behavior</li>
              <li>Expanded regression and architecture coverage to 149 tests, including service registration and dependency-boundary checks</li>
            </ul>

            <p><strong>RestfulToolkit Next 1.1.2</strong></p>
            <ul>
              <li>Fixed thread-safety issues in endpoint index rebuild and navigation (AtomicInteger retryCount, removed race conditions in getter-triggered rebuild)</li>
              <li>Improved navigation stability with fallback navigation and tryNavigate failure detection</li>
              <li>Refactored endpoint index error handling and state tracking: EndpointResolutionException, ensureRebuildScheduled, and RebuildResult</li>
              <li>Optimized async search execution to avoid EDT blocking and added result application callbacks</li>
              <li>Improved user feedback on navigation failures via SearchPopupActions status messages</li>
              <li>Fixed singleton state leak in GenerateUrlAction</li>
              <li>Optimized SpringWebFlux resolver by replacing FileTypeIndex with keyword pre-filtering</li>
              <li>Improved Spring placeholder parsing and JSON formatting error handling</li>
              <li>Enhanced Kotlin endpoint resolver type-safety checks</li>
              <li>Added synchronization protection and state copying for SearchHistory</li>
            </ul>

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
        sourceCompatibility = "25"
        targetCompatibility = "25"
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

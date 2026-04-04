plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.10.5"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
}

group = "com.sount"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdea("2026.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:

        composeUI()

        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }

    // RSyntaxTextArea for syntax highlighting
    implementation("com.fifesoft:rsyntaxtextarea:3.5.1")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }

        changeNotes = """
            <p><strong>RestfulToolkit Next</strong> re-release.</p>
            <ul>
              <li>Renamed and repackaged as RestfulToolkit Next</li>
              <li>Based on the original RestfulToolkit project for continued maintenance</li>
              <li>Adapted for newer IntelliJ IDEA / JetBrains Platform versions</li>
              <li>Updated plugin branding, icon assets, and project metadata</li>
            </ul>
        """.trimIndent()
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

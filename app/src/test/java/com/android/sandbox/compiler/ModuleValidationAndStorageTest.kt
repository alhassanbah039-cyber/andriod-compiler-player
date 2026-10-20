package com.android.sandbox.compiler

import com.android.sandbox.compiler.model.ProjectLanguage
import com.android.sandbox.compiler.model.SandboxFile
import com.android.sandbox.compiler.model.SandboxFileType
import com.android.sandbox.compiler.module1_storage.ProjectPayloadParser
import com.android.sandbox.compiler.module2_validator.DependencyBlocklistScanner
import com.android.sandbox.compiler.module2_validator.SecurityRuleBrokenException
import com.android.sandbox.compiler.module2_validator.SyntaxBracketValidator
import com.android.sandbox.compiler.module2_validator.SyntaxValidationException
import org.junit.Assert.*
import org.junit.Test

class ModuleValidationAndStorageTest {

    // ==================== MODULE 1: PAYLOAD PARSER TESTS ====================

    @Test
    fun testParseMultiFileStream() {
        val payload = """
            [META: LANGUAGE=JAVA, NAME=TestProject]
            [FILE: app/src/main/java/Main.java]
            public class Main {
                public void hello() {
                    System.out.println("Hello World");
                }
            }
            [FILE: app/build.gradle.kts]
            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
            }
        """.trimIndent()

        val parsed = ProjectPayloadParser.parse(payload)

        assertEquals(ProjectLanguage.JAVA, parsed.metadata.language)
        assertEquals("TestProject", parsed.metadata.explicitName)
        assertEquals(2, parsed.files.size)

        val javaFile = parsed.files.first { it.relativePath == "app/src/main/java/Main.java" }
        assertTrue(javaFile.content.contains("public class Main"))
        assertEquals(SandboxFileType.SOURCE_JAVA, javaFile.fileType)

        val gradleFile = parsed.files.first { it.relativePath == "app/build.gradle.kts" }
        assertTrue(gradleFile.content.contains("androidx.core:core-ktx"))
    }

    @Test
    fun testPathSanitizationAgainstTraversal() {
        val unsafePath = "../../etc/passwd"
        val safePath = ProjectPayloadParser.sanitizeRelativePath(unsafePath)
        assertFalse(safePath.contains(".."))
        assertEquals("etc/passwd", safePath)
    }

    // ==================== MODULE 2: SYNTAX BRACKET VALIDATOR TESTS ====================

    @Test
    fun testValidJavaKotlinBalancedBraces() {
        val validCode = """
            package com.example;
            // { Single line comment with brace
            /* Multi line comment { nested } */
            /* Nested /* comment with { */ */
            public class A {
                private String str = "{ not a brace }";
                private String raw = """
                    { raw brace }
                """;
                public void run() {
                    if (true) {
                        char c = '{';
                    }
                }
            }
        """.trimIndent()

        // Should pass without throwing
        SyntaxBracketValidator.validateKotlinOrJavaBraces("Test.kt", validCode)
    }

    @Test(expected = SyntaxValidationException::class)
    fun testUnbalancedClosingBraceThrows() {
        val invalidCode = """
            class A {
                void run() {}
            }
            }
        """.trimIndent()

        SyntaxBracketValidator.validateKotlinOrJavaBraces("Test.java", invalidCode)
    }

    @Test(expected = SyntaxValidationException::class)
    fun testUnclosedOpeningBraceThrows() {
        val invalidCode = """
            class A {
                void run() {
            }
        """.trimIndent()

        SyntaxBracketValidator.validateKotlinOrJavaBraces("Test.java", invalidCode)
    }

    @Test(expected = SyntaxValidationException::class)
    fun testUnclosedStringLiteralThrows() {
        val invalidCode = """
            class A {
                String s = "hello world
            }
        """.trimIndent()

        SyntaxBracketValidator.validateKotlinOrJavaBraces("Test.java", invalidCode)
    }

    @Test
    fun testValidXmlAngleBrackets() {
        val validXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- <commented-tag> -->
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <![CDATA[
                    <nested-cdata-tag>
                ]]>
                <application android:name=".App" android:label="<label>">
                    <activity android:name=".MainActivity" />
                </application>
            </manifest>
        """.trimIndent()

        // Should pass without throwing
        SyntaxBracketValidator.validateXmlAngleBrackets("AndroidManifest.xml", validXml)
    }

    @Test(expected = SyntaxValidationException::class)
    fun testUnclosedXmlTagThrows() {
        val invalidXml = """
            <manifest>
                <application
            </manifest>
        """.trimIndent()

        SyntaxBracketValidator.validateXmlAngleBrackets("bad.xml", invalidXml)
    }

    // ==================== MODULE 2: DEPENDENCY BLOCKLIST TESTS ====================

    @Test
    fun testPermittedCoreDependenciesPass() {
        val buildScript = """
            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
                implementation("androidx.compose.ui:ui:1.6.0")
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation(platform("androidx.compose:compose-bom:2024.02.00"))
            }
        """.trimIndent()

        val file = SandboxFile(
            relativePath = "app/build.gradle.kts",
            content = buildScript
        )

        // Should pass
        DependencyBlocklistScanner.scanFile(file)
    }

    @Test(expected = SecurityRuleBrokenException::class)
    fun testForbiddenThirdPartyDependencyThrows() {
        val maliciousBuildScript = """
            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
                implementation("com.squareup.retrofit2:retrofit:2.9.0")
            }
        """.trimIndent()

        val file = SandboxFile(
            relativePath = "app/build.gradle.kts",
            content = maliciousBuildScript
        )

        // Must throw SecurityRuleBrokenException
        DependencyBlocklistScanner.scanFile(file)
    }

    @Test(expected = SecurityRuleBrokenException::class)
    fun testMapNotationForbiddenDependencyThrows() {
        val maliciousBuildScript = """
            dependencies {
                implementation group: "org.apache.commons", name: "commons-lang3", version: "3.12.0"
            }
        """.trimIndent()

        val file = SandboxFile(
            relativePath = "app/build.gradle",
            content = maliciousBuildScript
        )

        DependencyBlocklistScanner.scanFile(file)
    }

    @Test
    fun testIngestAndValidateDynamicSandboxAppPayload() {
        val payload = """
            [META: LANGUAGE=Kotlin]
            [FILE: settings.gradle.kts]
            rootProject.name = "DynamicSandboxApp"

            [FILE: app/build.gradle.kts]
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }
            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
                implementation("androidx.compose.ui:ui:1.5.4")
            }

            [FILE: app/src/main/AndroidManifest.xml]
            <manifest xmlns:android="http://android.com" package="com.sandbox.app">
                <application android:label="Sandbox Dynamic App">
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>

            [FILE: app/src/main/java/com/sandbox/app/MainActivity.kt]
            package com.sandbox.app

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        HelloWorldScreen()
                    }
                }
            }

            @Composable
            fun HelloWorldScreen() {
                Text(text = "Hello from the Offline Sandbox Sandbox Engine!")
            }
        """.trimIndent()

        // 1. Ingest & Parse
        val parsed = ProjectPayloadParser.parse(payload)
        assertEquals(ProjectLanguage.KOTLIN, parsed.metadata.language)
        assertEquals(4, parsed.files.size)

        // 2. Validate all files through syntax and security validators
        for (file in parsed.files) {
            DependencyBlocklistScanner.scanFile(file)
            SyntaxBracketValidator.validateFile(file)
        }

        // Verify specific file types
        val settingsFile = parsed.files.first { it.relativePath == "settings.gradle.kts" }
        assertEquals(SandboxFileType.GRADLE_SETTINGS, settingsFile.fileType)

        val buildFile = parsed.files.first { it.relativePath == "app/build.gradle.kts" }
        assertEquals(SandboxFileType.GRADLE_BUILD, buildFile.fileType)

        val manifestFile = parsed.files.first { it.relativePath == "app/src/main/AndroidManifest.xml" }
        assertEquals(SandboxFileType.MANIFEST_XML, manifestFile.fileType)

        val ktFile = parsed.files.first { it.relativePath == "app/src/main/java/com/sandbox/app/MainActivity.kt" }
        assertEquals(SandboxFileType.SOURCE_KOTLIN, ktFile.fileType)
    }

    @Test
    fun testIngestAndValidateCppNativePayload() {
        val payload = """
            [META: LANGUAGE=CPP]
            [FILE: app/src/main/cpp/native-lib.cpp]
            #include <jni.h>
            #include <string>

            extern "C" JNIEXPORT jstring JNICALL
            Java_com_sandbox_app_MainActivity_stringFromJNI(JNIEnv* env, jobject /* this */) {
                std::string hello = "Hello from the Native ARM64 C++ Sandbox Engine!";
                return env->NewStringUTF(hello.c_str());
            }

            [FILE: app/build.gradle.kts]
            plugins {
                id("com.android.application")
            }
            android {
                externalNativeBuild {
                    cmake {
                        path = file("src/main/cpp/CMakeLists.txt")
                    }
                }
            }

            [FILE: app/src/main/cpp/CMakeLists.txt]
            cmake_minimum_required(VERSION 3.22.1)
            project("dynamicsandbox")
            add_library(Dynamic SHARED native-lib.cpp)
            find_library(log-lib log)
            target_link_libraries(Dynamic ${'$'}{log-lib})
        """.trimIndent()

        // 1. Ingest & Parse
        val parsed = ProjectPayloadParser.parse(payload)
        assertEquals(ProjectLanguage.CPP, parsed.metadata.language)
        assertEquals(3, parsed.files.size)

        // 2. Validate all files
        for (file in parsed.files) {
            DependencyBlocklistScanner.scanFile(file)
            SyntaxBracketValidator.validateFile(file)
        }

        val cppFile = parsed.files.first { it.relativePath == "app/src/main/cpp/native-lib.cpp" }
        assertEquals(SandboxFileType.SOURCE_CPP, cppFile.fileType)
        assertTrue(cppFile.content.contains("Java_com_sandbox_app_MainActivity_stringFromJNI"))

        val buildFile = parsed.files.first { it.relativePath == "app/build.gradle.kts" }
        assertEquals(SandboxFileType.GRADLE_BUILD, buildFile.fileType)

        val cmakeFile = parsed.files.first { it.relativePath == "app/src/main/cpp/CMakeLists.txt" }
        assertEquals(SandboxFileType.GENERIC_TEXT, cmakeFile.fileType)
    }
}



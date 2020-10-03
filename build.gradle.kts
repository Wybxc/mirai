@file:Suppress("UnstableApiUsage", "UNUSED_VARIABLE")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

buildscript {
    repositories {
        mavenLocal()
        // maven(url = "https://mirrors.huaweicloud.com/repository/maven")
        mavenCentral()
        jcenter()
        google()
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
        maven(url = "https://kotlin.bintray.com/kotlinx")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:${Versions.androidGradlePlugin}")
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${Versions.atomicFU}")
        classpath("org.jetbrains.kotlinx:binary-compatibility-validator:${Versions.binaryValidator}")
    }
}

plugins {
    kotlin("jvm") version Versions.kotlinCompiler
    kotlin("plugin.serialization") version Versions.kotlinCompiler
    id("org.jetbrains.dokka") version Versions.dokka apply false
    id("net.mamoe.kotlin-jvm-blocking-bridge") version Versions.blockingBridge apply false
    id("com.jfrog.bintray") version Versions.bintray
}

// https://github.com/kotlin/binary-compatibility-validator
//apply(plugin = "binary-compatibility-validator")


project.ext.set("isAndroidSDKAvailable", false)

// until
// https://youtrack.jetbrains.com/issue/KT-37152,
// are fixed.

/*
runCatching {
    val keyProps = Properties().apply {
        file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    if (keyProps.getProperty("sdk.dir", "").isNotEmpty()) {
        project.ext.set("isAndroidSDKAvailable", true)
    } else {
        project.ext.set("isAndroidSDKAvailable", false)
    }
}.exceptionOrNull()?.run {
    project.ext.set("isAndroidSDKAvailable", false)
}*/

allprojects {
    group = "net.mamoe"
    version = Versions.project

    repositories {
        mavenLocal()
        // maven(url = "https://mirrors.huaweicloud.com/repository/maven")
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
        maven(url = "https://kotlin.bintray.com/kotlinx")
        jcenter()
        google()
        mavenCentral()
    }

    afterEvaluate {
        configureJvmTarget()
        configureMppShadow()
        configureEncoding()
        configureKotlinTestSettings()
        configureKotlinCompilerSettings()
        configureKotlinExperimentalUsages()

        if (isKotlinJvmProject) {
            configureFlattenSourceSets()
        }

        configureDokka()
    }
}

fun Project.configureDokka() {
    apply(plugin = "org.jetbrains.dokka")
    tasks {
        val dokka by getting(DokkaTask::class) {
            outputFormat = "html"
            outputDirectory = "$buildDir/dokka"
        }
        val dokkaMarkdown by creating(DokkaTask::class) {
            outputFormat = "markdown"
            outputDirectory = "$buildDir/dokka-markdown"
        }
        val dokkaGfm by creating(DokkaTask::class) {
            outputFormat = "gfm"
            outputDirectory = "$buildDir/dokka-gfm"
        }
    }
    for (task in tasks.filterIsInstance<DokkaTask>()) {
        task.configuration {
            perPackageOption {
                prefix = "net.mamoe.mirai"
                skipDeprecated = true
            }
            for (suppressedPackage in arrayOf(
                "net.mamoe.mirai.internal",
                "net.mamoe.mirai.event.internal",
                "net.mamoe.mirai.utils.internal",
                "net.mamoe.mirai.internal"
            )) {
                perPackageOption {
                    prefix = suppressedPackage
                    suppress = true
                }
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE") // or error
fun Project.configureJvmTarget() {
    tasks.withType(KotlinJvmCompile::class.java) {
        kotlinOptions.jvmTarget = "11"
    }

    extensions.findByType(JavaPluginExtension::class.java)?.run {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

fun Project.configureMppShadow() {
    val kotlin =
        runCatching {
            (this as ExtensionAware).extensions.getByName("kotlin") as? KotlinMultiplatformExtension
        }.getOrNull() ?: return

    val shadowJvmJar by tasks.creating(ShadowJar::class) sd@{
        group = "mirai"
        archiveClassifier.set("-all")

        val compilations =
            kotlin.targets.filter { it.platformType == KotlinPlatformType.jvm }
                .map { it.compilations["main"] }

        compilations.forEach {
            dependsOn(it.compileKotlinTask)
            from(it.output)
        }

        println(project.configurations.joinToString())

        from(project.configurations.getByName("jvmRuntimeClasspath"))

        this.exclude { file ->
            file.name.endsWith(".sf", ignoreCase = true)
        }

        /*
        this.manifest {
            this.attributes(
                "Manifest-Version" to 1,
                "Implementation-Vendor" to "Mamoe Technologies",
                "Implementation-Title" to this.name.toString(),
                "Implementation-Version" to this.version.toString()
            )
        }*/
    }
}

fun Project.configureEncoding() {
    tasks.withType(JavaCompile::class.java) {
        options.encoding = "UTF8"
    }
}

fun Project.configureKotlinTestSettings() {
    tasks.withType(Test::class) {
        useJUnitPlatform()
    }
    when {
        isKotlinJvmProject -> {
            dependencies {
                testImplementation(kotlin("test-junit5"))

                testApi("org.junit.jupiter:junit-jupiter-api:5.2.0")
                testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.2.0")
            }
        }
        isKotlinMpp -> {
            kotlinSourceSets?.forEach { sourceSet ->
                if (sourceSet.name == "common") {
                    sourceSet.dependencies {
                        implementation(kotlin("test"))
                        implementation(kotlin("test-annotations-common"))
                    }
                } else {
                    sourceSet.dependencies {
                        implementation(kotlin("test-junit5"))

                        implementation("org.junit.jupiter:junit-jupiter-api:5.2.0")
                        implementation("org.junit.jupiter:junit-jupiter-engine:5.2.0")
                    }
                }
            }
        }
    }
}

fun Project.configureKotlinCompilerSettings() {
    val kotlinCompilations = kotlinCompilations ?: return
    for (kotlinCompilation in kotlinCompilations) with(kotlinCompilation) {
        if (isKotlinJvmProject) {
            @Suppress("UNCHECKED_CAST")
            this as KotlinCompilation<KotlinJvmOptions>
        }
        kotlinOptions.freeCompilerArgs += "-Xjvm-default=all"
    }
}

val experimentalAnnotations = arrayOf(
    "kotlin.RequiresOptIn",
    "kotlin.contracts.ExperimentalContracts",
    "kotlin.experimental.ExperimentalTypeInference",
    "net.mamoe.mirai.utils.MiraiInternalAPI",
    "net.mamoe.mirai.utils.MiraiExperimentalAPI",
    "net.mamoe.mirai.LowLevelAPI",
    "kotlinx.serialization.ExperimentalSerializationApi"
)

fun Project.configureKotlinExperimentalUsages() {
    val sourceSets = kotlinSourceSets ?: return

    for (target in sourceSets) {
        experimentalAnnotations.forEach { a ->
            target.languageSettings.useExperimentalAnnotation(a)
            target.languageSettings.progressiveMode = true
            target.languageSettings.enableLanguageFeature("InlineClasses")
        }
    }
}

fun Project.configureFlattenSourceSets() {
    sourceSets {
        findByName("main")?.apply {
            resources.setSrcDirs(listOf(projectDir.resolve("resources")))
            java.setSrcDirs(listOf(projectDir.resolve("src")))
        }
        findByName("test")?.apply {
            resources.setSrcDirs(listOf(projectDir.resolve("resources")))
            java.setSrcDirs(listOf(projectDir.resolve("test")))
        }
    }
}

val Project.kotlinSourceSets get() = extensions.findByName("kotlin").safeAs<KotlinProjectExtension>()?.sourceSets

val Project.kotlinTargets
    get() =
        extensions.findByName("kotlin").safeAs<KotlinSingleTargetExtension>()?.target?.let { listOf(it) }
            ?: extensions.findByName("kotlin").safeAs<KotlinMultiplatformExtension>()?.targets

val Project.isKotlinJvmProject: Boolean get() = extensions.findByName("kotlin") is KotlinJvmProjectExtension
val Project.isKotlinMpp: Boolean get() = extensions.findByName("kotlin") is KotlinMultiplatformExtension

val Project.kotlinCompilations
    get() = kotlinTargets?.flatMap { it.compilations }
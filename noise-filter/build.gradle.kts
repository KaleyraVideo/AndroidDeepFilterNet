import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import org.jetbrains.dokka.gradle.DokkaTask
import java.util.Locale

val libraryGroupId = "io.github.kaleyravideo"
val libraryArtifactId = "android-deepfilternet"
val libraryVersion = "0.0.0"

val bundledModelFlavour = "bundledModel"
val lazyModelFlavour = "lazyModel"

group = libraryGroupId
version = libraryVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.jetbrains.dokka)
    alias(libs.plugins.jk1.license.report)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.signing)
}

android {
    namespace = "com.kaleyra.noise_filter"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    flavorDimensions += "library_type"
    productFlavors {
        create(bundledModelFlavour) {
            isDefault = true
            dimension = "library_type"
        }
        create(lazyModelFlavour) {
            dimension = "library_type"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

configure<SigningExtension> {
    useInMemoryPgpKeys(
        System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyId")
            ?: project.properties["signingInMemoryKeyId"]?.toString(),
        System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey")
            ?: project.properties["signingInMemoryKey"]?.toString(),
        System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyPassword")
            ?: project.properties["signingInMemoryKeyPassword"]?.toString(),
    )
}

fun Project.configureDokkaTaskForVariant(name: String, variant: LibraryVariant): TaskProvider<DokkaTask> {
    return tasks.register<DokkaTask>(name) {
        moduleName.set("${project.name}-${variant.flavorName}")
        moduleVersion.set(project.version.toString())

        outputDirectory.set(layout.buildDirectory.dir("dokka/${variant.flavorName}"))

        // Configure source sets for this specific Dokka task
        // This is crucial to ensure Dokka processes the correct sources for the variant
        dokkaSourceSets {
            named("main") { // Refers to the source set named "main" (e.g., main/java, main/kotlin)
                classpath.from(variant.compileConfiguration)
                // Add variant-specific source directories
                sourceRoots.from(
                    android.sourceSets.findByName(variant.name)?.java?.srcDirs,
                    android.sourceSets.findByName("main")?.java?.srcDirs // Include main source sets if applicable
                )
            }
        }
    }
}

fun Project.configureJavadocTaskForVariant(
    name: String,
    dokkaTask: TaskProvider<DokkaTask>,
    variant: LibraryVariant,
): TaskProvider<Jar> {
    val javaDocDir = "javadoc-jars/${variant.flavorName}"
    val javaDocsJarName = "${project.name}-${variant.flavorName}-javadoc.jar"
    return tasks.register<Jar>(name) {
        dependsOn(dokkaTask)
        from(dokkaTask.flatMap { it.outputDirectory })

        archiveClassifier.set("javadoc")
        destinationDirectory.set(layout.buildDirectory.dir(javaDocDir))
        archiveFileName.set(javaDocsJarName)
    }
}

fun Project.createMavenPublicationForVariant(
    name: String,
    variant: LibraryVariant,
    artifactId: String,
    javaDocTask: TaskProvider<Jar>,
): MavenPublication {
    val publication = publishing.publications.create<MavenPublication>(name) {
        this@create.groupId = project.group.toString()
        this@create.artifactId = artifactId
        this@create.version = project.version.toString()

        from(components.findByName(variant.name))
        artifact(javaDocTask)

        pom {
            this@pom.name = "AndroidDeepFilterNet-${variant.flavorName.capitalize(Locale.getDefault())}"
            this@pom.description = "An Android library providing noise filtering capabilities derived from the DeepFilterNet project (${variant.flavorName} variant)."
            this@pom.inceptionYear = "2025"
            this@pom.url = "https://github.com/KaleyraVideo/AndroidDeepFilterNet"
            licenses {
                license {
                    this@license.name = "The Apache License, Version 2.0"
                    this@license.url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    this@license.distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                }
            }
            developers {
                developer {
                    this@developer.id = "kaleyra-video"
                    this@developer.name = "Kaleyra Video"
                    this@developer.url = "https://github.com/KaleyraVideo/"
                }
            }
            scm {
                this@scm.url = "https://github.com/KaleyraVideo/"
                this@scm.connection = "scm:git:git://github.com/KaleyraVideo/AndroidDeepFilterNet.git"
                this@scm.developerConnection = "scm:git:ssh://git@github.com/KaleyraVideo/AndroidDeepFilterNet.git"
            }
        }
    }
    return publication
}

fun Project.configureChecksumTaskForVariant(
    name: String,
    artifactId: String
): TaskProvider<Checksum> {
    return tasks.register<Checksum>(name) {
        val homeDir = System.getProperty("user.home")
        val m2Dir = ".m2/repository"
        val groupDir = project.group.toString().replace(".", "/")
        val path = listOf(homeDir, m2Dir, groupDir, artifactId, project.version.toString()).joinToString("/")

        val outputDir = File(path)
        val outputFileTree = fileTree(path) {
            exclude("**/*.asc")
        }

        inputFiles.setFrom(outputFileTree)
        outputDirectory.set(outputDir)
        checksumAlgorithms.set(listOf(Checksum.Algorithm.MD5, Checksum.Algorithm.SHA1))
    }
}

fun Project.configurePublishSignChecksumDependencies(
    publicationName: String,
    variantName: String,
    checksumTask: TaskProvider<Checksum>
) {
    val publishToMavenLocalTask = tasks.named("publish${publicationName}PublicationToMavenLocal")

    checksumTask.dependsOn(publishToMavenLocalTask)

    tasks.register("publishSignChecksum${variantName.capitalize(Locale.getDefault())}ToMavenLocal") {
        group = "publishing"
        description = "Publishes, signs, and checksums (excluding signatures) the $variantName variant artifacts in .m2."
        dependsOn(checksumTask)
    }
}

afterEvaluate {
    android.libraryVariants.forEach { variant ->
        if (variant.buildType.name == "release") {
            val flavorName = variant.flavorName
            val isBundledModelFlavor = flavorName == bundledModelFlavour

            val dokkaTaskName = "dokkaHtml${flavorName.capitalize(Locale.getDefault())}"
            val dokkaTask = configureDokkaTaskForVariant(dokkaTaskName, variant)

            val javaDocTaskName = "javaDocJar${flavorName.capitalize(Locale.getDefault())}"
            val javaDocTask = configureJavadocTaskForVariant(
                name = javaDocTaskName,
                variant = variant,
                dokkaTask = dokkaTask,
            )

            val publicationName = flavorName.capitalize(Locale.getDefault())
            val variantId = if (!isBundledModelFlavor) "-${flavorName.lowercase(Locale.getDefault())}" else ""
            val artifactId = libraryArtifactId + variantId
            val publication = createMavenPublicationForVariant(
                name = publicationName,
                variant = variant,
                artifactId = artifactId,
                javaDocTask = javaDocTask
            )

            signing.sign(publication)

            val checksumTaskName = "checksum${flavorName.capitalize(Locale.getDefault())}"
            val checksumTask = configureChecksumTaskForVariant(checksumTaskName, artifactId)

            configurePublishSignChecksumDependencies(
                publicationName = publicationName,
                variantName = flavorName,
                checksumTask = checksumTask
            )
        }
    }
}

tasks.register("publishSignChecksumAllPublicationsToMavenLocal") {
    group = "publishing"
    description = "Publishes, signs, and checksums (excluding signatures) all variants artifacts in .m2."
    val tasks = android.libraryVariants.map { variant ->
        val variantName = variant.flavorName.capitalize(Locale.getDefault())
        tasks.named("publishSignChecksum${variantName}ToMavenLocal")
    }
    tasks.forEach { dependsOn(it) }
}

tasks.register("zipMavenLocalPublications", Zip::class) {
    dependsOn(tasks.named("publishSignChecksumAllPublicationsToMavenLocal"))
    val homeDir = System.getProperty("user.home")
    val m2Dir = "/.m2/repository"
    archiveFileName.set("${project.name}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("zips"))

    from(file("$homeDir$m2Dir")) {
        exclude("**/maven-metadata-local.xml")
    }
}

private fun String.capitalize(locale: Locale): String = replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(locale) else it.toString()
}

tasks.register<UploadZipTask>("publishAllFlavoursToMavenCentral") {
    dependsOn(tasks.named("zipMavenLocalPublications"))
    group = "publishing"
    description = "Publish both flavours on Maven Central."

    zipFile.set(layout.buildDirectory.file("zips/noise-filter.zip"))
    authUsername.set(
        System.getenv("ORG_GRADLE_PROJECT_mavenCentralUsername")
            ?: project.properties["mavenCentralUsername"]?.toString()
    )
    authToken.set(
        System.getenv("ORG_GRADLE_PROJECT_mavenCentralPassword")
            ?: project.properties["mavenCentralPassword"]?.toString()
    )

    val name = "${project.group}:${project.name}:${project.version}"
    uploadUrl.set("https://central.sonatype.com/api/v1/publisher/upload?publishingType=USER_MANAGED&name=$name")
}

dependencies {
    dokkaPlugin(libs.jetbrains.dokka)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kaleyra.video.utils)
    implementation(libs.jetbrains.coroutines.core)

    "lazyModelImplementation"(libs.ktor.client.core)
    "lazyModelImplementation"(libs.ktor.client.android)
    "lazyModelImplementation"(libs.ktor.client.content.negotiation)
    "lazyModelImplementation"(libs.ktor.serialization.kotlinx.json)
    "lazyModelImplementation"(libs.kotlinx.serialization.json)
    "lazyModelImplementation"(libs.ktor.client.logging)
    "lazyModelImplementation"(libs.ktor.client.logging.jvm)
    "lazyModelImplementation"(libs.ktor.client.logging.jvm)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

licenseReport {
    outputDir = "$rootDir"
    projects = arrayOf(project) + project.subprojects
    configurations = arrayOf("releaseRuntimeClasspath")
    renderers =
        arrayOf(InventoryHtmlReportRenderer("THIRD-PARTY-LICENSES.html", "Third party libraries"))
}

tasks.register<Exec>("moduleLicenseReport") {
    workingDir = rootDir
    description = "Executes the generateLicenseReport Gradle task"
    commandLine("./gradlew", "generateLicenseReport")
}

tasks.named("preBuild") {
    dependsOn("moduleLicenseReport")
}

tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens", "java.base/java.io=ALL-UNNAMED"
    )
}
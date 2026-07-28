import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.dokka)
    alias(libs.plugins.maven.publish)
}

group = gropify.project.groupName
version = gropify.project.yukihookapi.dexkit.version

android {
    namespace = "${gropify.project.groupName}.dexkit"
    compileSdk = gropify.project.android.compileSdk

    defaultConfig {
        minSdk = gropify.project.android.minSdk
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    lint { checkReleaseBuilds = false }
}

dependencies {
    api(projects.yukihookapiCore)
    api(libs.dexkit)
}

mavenPublishing {
    configure(AndroidSingleVariantLibrary(JavadocJar.None(), SourcesJar.Sources()))
    coordinates(
        groupId = group.toString(),
        artifactId = gropify.project.yukihookapi.dexkit.moduleName,
        version = version.toString()
    )
}
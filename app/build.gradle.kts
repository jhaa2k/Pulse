import java.net.URI
import java.net.URLEncoder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.vexo.studio"
    sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/studioFonts/res"))
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/studioFonts/assets"))
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vexo.studio"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "3.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            val customDebugKeystore = file("${rootDir}/debug.keystore")
            if (customDebugKeystore.exists()) {
                storeFile = customDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
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
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.palette:palette-ktx:1.0.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

val fontManifest = rootProject.file("tools/studio-fonts.txt")
val fontOutput = layout.buildDirectory.dir("generated/studioFonts")
val fontRevision = "40cd5edf3831c9e0012b96345c476598595b9970"
val prepareStudioFonts = tasks.register("prepareStudioFonts") {
    inputs.file(fontManifest)
    inputs.property("fontRevision",fontRevision)
    outputs.dir(fontOutput)
    doLast {
        val root=fontOutput.get().asFile
        fontManifest.readLines().filter{it.isNotBlank()}.forEach { row ->
            val (source,dest)=row.split('|')
            val target=root.resolve(dest)
            if(target.isFile && target.length()>1024) return@forEach
            target.parentFile.mkdirs()
            val path=source.split('/').joinToString("/") { URLEncoder.encode(it,"UTF-8").replace("+","%20") }
            val url=URI("https://raw.githubusercontent.com/google/fonts/"+fontRevision+"/ofl/"+path).toURL()
            val temp=File(target.parentFile,target.name+".partial")
            var last:Exception?=null
            repeat(3) {
                if(last==null && target.isFile && target.length()>1024)return@repeat
                try {
                    val connection=url.openConnection().apply{connectTimeout=20000;readTimeout=40000}
                    connection.getInputStream().use{input->temp.outputStream().use{output->input.copyTo(output)}}
                    require(temp.length()>1024){"Invalid font source: "+source}
                    if(dest.endsWith(".ttf")){
                        val signature=temp.inputStream().use{it.readNBytes(4)}
                        require(signature.contentEquals(byteArrayOf(0,1,0,0)) || String(signature,Charsets.US_ASCII)=="OTTO"){"Invalid font binary: "+source}
                    }
                    Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)
                    last=null
                }catch(e:Exception){temp.delete();last=e}
            }
            last?.let{throw it}
        }
    }
}
tasks.named("preBuild"){dependsOn(prepareStudioFonts)}

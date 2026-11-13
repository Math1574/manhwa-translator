plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Paramètres de la compilation en ligne : numéro du build (la mise à jour intégrée le compare à celui des publications) et architectures à embarquer
// (le téléphone est en arm64 seul ; le test sur émulateur compile en x86_64). Sans paramètre : toutes les architectures, numéro 0.
val buildNumber = (project.findProperty("buildNumber") as String?)?.trim()?.toIntOrNull() ?: 0
val abiList = (project.findProperty("abis") as String?)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

android {
    namespace = "app.manhwa"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.manhwa.translator"
        minSdk = 26
        targetSdk = 35
        // Le code de version reste à 1 : Android accepte alors la réinstallation de n'importe quel build par-dessus un autre (retour en arrière compris).
        // La mise à jour intégrée compare BUILD_NUMBER, pas ce code.
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("int", "BUILD_NUMBER", buildNumber.toString())
        if (abiList.isNotEmpty()) ndk { abiFilters += abiList }
    }
    // Clé de signature fixe : la compilation en ligne la récupère dans un espace privé du dépôt et la donne par variables d'environnement.
    // Sans elle (compilation locale), Android Studio signe avec sa clé de débogage habituelle. Toujours la même clé = mises à jour sans désinstaller.
    val keystorePath = System.getenv("SIGNING_KEYSTORE_FILE")
    if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
        signingConfigs {
            create("fixed") {
                storeFile = file(keystorePath)
                storeType = "pkcs12"
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
        buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("fixed") } }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.12.1")

    // OCR local (modèles embarqués dans l'APK)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    // Traduction locale (modèles téléchargés une fois par langue)
    implementation("com.google.mlkit:translate:17.0.3")
    // Second moteur de lecture : PaddleOCR (modèles ONNX) pour le texte stylisé ou hors bulles
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    // Traduction locale avec contexte : modèle de langage Gemma 4 via LiteRT-LM
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")
}

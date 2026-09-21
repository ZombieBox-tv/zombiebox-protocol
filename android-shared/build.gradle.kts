plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "io.github.diegog0477.zombiebox.shared"
    compileSdk = 35
    defaultConfig { minSdk = 9 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }

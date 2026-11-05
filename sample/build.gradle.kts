plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvm()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":engram-compose"))
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.material3)
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.muhammedcakirgoz.engram.sample.MainKt"
    }
}

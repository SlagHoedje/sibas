plugins {
    kotlin("jvm") version "1.5.20"
    id("application")
}

group = "nl.chrisb"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://m2.dv8tion.net/releases")
    maven("https://jitpack.io/")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.5.1")

    implementation("net.dv8tion:JDA:4.3.0_277")
    implementation("com.github.minndevelopment:jda-ktx:ea0a1b2")

    implementation("com.zaxxer:HikariCP:2.3.2")
    implementation("org.postgresql:postgresql:42.2.23")

    implementation("com.github.the-codeboy:Piston4J:0.0.6")
}

application {
    mainClass.set("nl.chrisb.sibas.MainKt")
}

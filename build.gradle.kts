plugins {
    kotlin("jvm") version "1.5.0"
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

    implementation("net.dv8tion:JDA:4.3.0_277")
    implementation("com.github.minndevelopment:jda-ktx:ea0a1b2")

    implementation("com.zaxxer:HikariCP:2.3.2")
    implementation("org.postgresql:postgresql:42.2.23")
}

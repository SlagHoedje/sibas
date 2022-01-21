plugins {
    kotlin("jvm") version "1.5.10"
}

group = "nl.chrisb"
version = "2.0"

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("dev.kord:kord-core:0.8.0-M8")
}

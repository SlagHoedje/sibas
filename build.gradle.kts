plugins {
    kotlin("jvm") version "1.5.10"
}

group = "nl.chrisb"
version = "2.0"

repositories {
    mavenCentral()
    maven("https://maven.kotlindiscord.com/repository/maven-public/")
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("com.kotlindiscord.kord.extensions:kord-extensions:1.5.1-RC1")

    implementation("org.jetbrains.exposed:exposed-core:0.37.3")
    implementation("org.jetbrains.exposed:exposed-dao:0.37.3")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.37.3")
    implementation("org.jetbrains.exposed:exposed-java-time:0.37.3")
    implementation("com.h2database:h2:2.1.210")
}

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
}

plugins {
    application
    kotlin("jvm") version "1.5.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "nl.chrisb"
version = "2.0"

repositories {
    mavenCentral()
    maven("https://maven.kotlindiscord.com/repository/maven-public/")
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.17.1")
    implementation("io.github.microutils:kotlin-logging-jvm:2.1.20")

    implementation("com.kotlindiscord.kord.extensions:kord-extensions:1.5.1-RC1")

    implementation("org.jetbrains.exposed:exposed-core:0.37.3")
    implementation("org.jetbrains.exposed:exposed-dao:0.37.3")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.37.3")
    implementation("org.jetbrains.exposed:exposed-java-time:0.37.3")

    implementation("com.h2database:h2:2.1.210")
    implementation("org.postgresql:postgresql:42.3.1")
    implementation("mysql:mysql-connector-java:8.0.25")
}

application {
    mainClass.set("nl.chrisb.sibas.SibasKt")
}

plugins {
    kotlin("jvm") version "2.2.0"
    application
}

group = "org.redlin"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("org.redlin.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}
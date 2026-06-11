plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

group = "kr.raaaaming"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    testImplementation(kotlin("test"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly(files("libs/arc-core-1.0.0.jar"))
    ksp(files("libs/arc-ksp-1.0.0.jar"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

plugins {
	kotlin("jvm") version "2.3.21"
	id("com.google.devtools.ksp") version "2.3.7"
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
	compileOnly(files("libs\\arc-core-1.0.0-Beta.jar"))
	ksp(files("libs\\arc-ksp-1.0.0-Beta.jar"))
}

kotlin {
	jvmToolchain(21)
}

tasks.test {
	useJUnitPlatform()
}
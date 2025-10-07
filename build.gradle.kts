plugins {
    id("java-library")
    id("maven-publish")
}

group = "de.jexcellence.dependency"
version = "2.0.0"

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")
    implementation("org.ow2.asm:asm-util:9.6")

    // Gradle API for the Gradle plugin (compileOnly since it's provided by Gradle)
    compileOnly(gradleApi())
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "JEDependency"
            version = project.version.toString()
            
            pom {
                name.set("JEDependency")
                description.set("Dependency injection library with repository downloads and module deencapsulation")
                url.set("https://github.com/jexcellence/JEDependency")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("jexcellence")
                        name.set("JExcellence")
                    }
                }
            }
        }
    }
    repositories {
        mavenLocal()
    }
}
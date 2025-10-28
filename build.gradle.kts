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
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")

    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")
    implementation("org.ow2.asm:asm-util:9.6")

    // Gradle API for the Gradle plugin (compileOnly since it's provided by Gradle)
    compileOnly(gradleApi())
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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

                scm {
                    url.set("https://github.com/jexcellence/JEDependency")
                    connection.set("scm:git:https://github.com/jexcellence/JEDependency.git")
                    developerConnection.set("scm:git:ssh://git@github.com/jexcellence/JEDependency.git")
                }
            }
        }
    }
    repositories {
        mavenLocal()
    }
}
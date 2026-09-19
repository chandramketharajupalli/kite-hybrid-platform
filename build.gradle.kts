plugins {
    base
    id("org.springframework.boot") version "3.5.16" apply false
}
allprojects {
    group = "com.kitehybrid"
    version = "0.1.0-SNAPSHOT"
    repositories { mavenCentral() }
}
tasks.named("check") { dependsOn(":trading-core:check") }
tasks.named("build") { dependsOn(":trading-core:build") }

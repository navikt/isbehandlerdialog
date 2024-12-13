group = "no.nav.syfo"
version = "0.0.1"

val flywayVersion = "10.21.0"
val googleCloudStorageVersion = "2.45.0"
val hikariVersion = "5.1.0"
val jacksonDataTypeVersion = "2.17.2"
val kafkaVersion = "3.7.0"
val kluentVersion = "1.73"
val ktorVersion = "3.0.2"
val logbackVersion = "1.5.7"
val logstashEncoderVersion = "7.4"
val micrometerRegistryVersion = "1.12.2"
val mockkVersion = "1.13.9"
val nimbusJoseJwtVersion = "9.37.2"
val postgresVersion = "42.7.4"
val postgresEmbeddedVersion = "2.0.7"
val spekVersion = "2.0.19"

plugins {
    kotlin("jvm") version "2.0.20"
    id("com.gradleup.shadow") version "8.3.2"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryVersion")

    // Database
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    testImplementation("io.zonky.test:embedded-postgres:$postgresEmbeddedVersion")

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonDataTypeVersion")

    // Kafka
    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
    }
    implementation("org.apache.kafka:kafka_2.13:$kafkaVersion", excludeLog4j)
    constraints {
        implementation("org.apache.zookeeper:zookeeper") {
            because("org.apache.kafka:kafka_2.13:$kafkaVersion -> https://www.cve.org/CVERecord?id=CVE-2023-44981")
            version {
                require("3.9.3")
            }
        }
    }
    implementation("com.google.cloud:google-cloud-storage:$googleCloudStorageVersion")

    // Tests
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwtVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    jar {
        manifest.attributes["Main-Class"] = "no.nav.syfo.AppKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    shadowJar {
        mergeServiceFiles()
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    test {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}

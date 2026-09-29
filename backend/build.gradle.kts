buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("com.guardsquare:proguard-gradle:7.6.1") }
}

plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.company"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val queryDslVersion = "6.12"   // OpenFeign 유지보수 포크(io.github.openfeign.querydsl), CVE-2024-49203 수정
val jjwtVersion = "0.12.6"
val mapstructVersion = "1.6.3"
val poiVersion = "5.4.0"

// 보안 패치: BOM 관리 버전을 CVE 수정본으로 상향
extra["jackson-bom.version"] = "2.21.5"      // CVE-2026-54515
extra["commons-lang3.version"] = "3.18.0"    // CVE-2025-48924

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Database & migration
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // API documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // QueryDSL — OpenFeign 유지보수 포크(패키지 com.querydsl.* 호환, jakarta 기본)
    implementation("io.github.openfeign.querydsl:querydsl-jpa:$queryDslVersion")
    annotationProcessor("io.github.openfeign.querydsl:querydsl-apt:$queryDslVersion:jakarta")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // MapStruct
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")

    // Excel (Apache POI)
    implementation("org.apache.poi:poi-ooxml:$poiVersion")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    // Lombok + MapStruct binding
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // Development
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

// Generated QueryDSL Q-classes location
val generatedDir = "build/generated/sources/annotationProcessor/java/main"
sourceSets {
    main {
        java {
            srcDir(generatedDir)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 실행 가능한 Boot jar 하나만 생성하도록 plain jar 비활성화 (Docker COPY 단순화)
tasks.named<Jar>("jar") {
    enabled = false
}

// 로컬 개발 실행은 local 프로파일로(기본 활성 프로파일은 prod 이므로).
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    args("--spring.profiles.active=local")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
}

// ── 프론트엔드를 빌드해 jar 내부 static/ 으로 번들 ──────────────────
val isWindows = System.getProperty("os.name").lowercase().contains("win")
// junction(C:\alwork) 이 아닌 실제 경로에서 Vite 를 실행해야 함(상대경로 이슈 회피)
val frontendDir = file("../frontend").canonicalFile

tasks.register<Exec>("frontendBuild") {
    workingDir = frontendDir
    // node_modules 없으면 먼저 `npm ci` 필요. 여기서는 빌드만 수행.
    if (isWindows) commandLine("cmd", "/c", "npm", "run", "build")
    else commandLine("npm", "run", "build")
}

tasks.register<Copy>("copyFrontend") {
    dependsOn("frontendBuild", "processResources")
    from(file("../frontend/dist"))
    into(layout.buildDirectory.dir("resources/main/static"))
}

// 일반 jar 도 프론트 정적파일 포함 (난독화 태스크는 자체 정의에서 copyFrontend 의존)
tasks.named("bootJar") { dependsOn("copyFrontend") }

// 1) 우리 클래스만 난독화 → jar 로 출력 (deps 는 라이브러리 참조)
val obfClassesJar = layout.buildDirectory.file("obf/obf-classes.jar")
val obfClassesDir = layout.buildDirectory.dir("obf/classes")
tasks.register<proguard.gradle.ProGuardTask>("proguardClasses") {
    dependsOn("classes", "copyFrontend")
    injars(tasks.named("compileJava").get().outputs.files)
    outjars(obfClassesJar.get().asFile)

    libraryjars(configurations.getByName("runtimeClasspath"))
    val javaHome = System.getProperty("java.home")
    listOf(
        "java.base", "java.sql", "java.naming", "java.management", "java.instrument",
        "java.desktop", "java.xml", "java.net.http", "java.rmi", "java.compiler",
        "java.scripting", "java.prefs", "java.security.jgss", "java.security.sasl",
        "java.logging", "java.datatransfer", "jdk.unsupported"
    ).forEach { module ->
        libraryjars(
            mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
            "$javaHome/jmods/$module.jmod"
        )
    }
    configuration("proguard-rules.pro")
}

// 2) 난독화 jar 를 클래스 디렉터리로 풀기
tasks.register<Copy>("explodeObfClasses") {
    dependsOn("proguardClasses")
    from(zipTree(obfClassesJar))
    into(obfClassesDir)
}

// 3) 난독화된 클래스 + 리소스 + 의존성으로 실행형 boot jar 재패키징
tasks.register<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJarObf") {
    dependsOn("explodeObfClasses")
    mainClass.set("com.company.leave.LeaveManagementApplication")
    targetJavaVersion.set(JavaVersion.VERSION_21)
    archiveFileName.set("backend-obf.jar")
    // 운영 아티팩트에 spring-boot-devtools 가 섞이지 않도록 제외 (보안·정보노출 방지)
    val runtimeNoDevtools = configurations.getByName("runtimeClasspath")
        .filter { !it.name.startsWith("spring-boot-devtools") }
    classpath(
        obfClassesDir,
        tasks.named("processResources").get().outputs.files,
        runtimeNoDevtools
    )
}

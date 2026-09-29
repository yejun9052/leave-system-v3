plugins {
    // 빌드에 필요한 JDK(21) 툴체인이 없을 경우 자동으로 내려받아 사용
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "annual-leave"

include("backend")

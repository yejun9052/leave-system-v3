# =====================================================================
# ProGuard 규칙 (Spring Boot fat jar) — `gradlew proguardJar` 로 실행
# 전략: 우리 클래스 "이름"은 보존(엔티티 JPQL/스캔/매핑 안전),
#       서비스 내부 "멤버(메서드/필드) 이름"은 난독화.
#       엔티티/DTO/레코드/리포지토리/파라미터명/애노테이션 요소는 보존.
# =====================================================================

-dontshrink
-dontoptimize
-dontwarn **
-ignorewarnings
-dontnote **
-verbose

# 리플렉션/직렬화/바인딩에 필요한 속성 보존
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Record,RecordComponents,Exceptions
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# @RequestParam/@PathVariable/@ConfigurationProperties/Jackson/record 가 파라미터명을 사용 → 보존
# Spring 6 은 MethodParameters 속성을 읽으므로 반드시 보존
-keepattributes MethodParameters
-keepparameternames

# 서드파티/프레임워크는 그대로(우리 코드만 대상), Spring Boot 로더 포함
-keep class org.springframework.** { *; }
-keep class jakarta.** { *; }
-keep class com.fasterxml.** { *; }
-keep class org.hibernate.** { *; }
-keep class com.querydsl.** { *; }
-keep class org.flywaydb.** { *; }
-keep class io.swagger.** { *; }

# --- 우리 코드: 클래스 "이름"은 보존(멤버는 난독화 허용) ---
-keep class com.company.leave.** { <init>(...); }

# 메인 클래스는 전체 보존
-keep class com.company.leave.LeaveManagementApplication { *; }

# @Aspect: @Pointcut/@Around 가 메서드명을 문자열로 참조 → 애스펙트는 원형 보존(멤버 이름 유지)
-keep class com.company.leave.audit.AuditAspect { *; }

# 엔티티/DTO/레코드/리포지토리 멤버는 보존(JPA/JSON/파생쿼리)
-keepclassmembers class com.company.leave.**.domain.** { *; }
-keepclassmembers class com.company.leave.**.dto.** { *; }
-keepclassmembers class com.company.leave.common.entity.** { *; }
-keepclassmembers interface com.company.leave.**.repository.** { *; }
# 패키지 위치와 무관하게 모든 Spring Data 리포지토리의 메서드명 보존(파생쿼리)
-keepclassmembers interface * extends org.springframework.data.repository.Repository { *; }
-keepclassmembers class * extends java.lang.Record { *; }

# 스프링이 리플렉션으로 호출/주입하는 멤버는 보존
-keepclassmembers class com.company.leave.** {
    @org.springframework.context.annotation.Bean <methods>;
    @org.springframework.scheduling.annotation.Scheduled <methods>;
    @org.springframework.context.event.EventListener <methods>;
    @org.springframework.transaction.event.TransactionalEventListener <methods>;
    @jakarta.annotation.PostConstruct <methods>;
    @jakarta.annotation.PreDestroy <methods>;
    @jakarta.persistence.PersistenceContext *;
    @org.springframework.beans.factory.annotation.Autowired *;
    @org.springframework.beans.factory.annotation.Value *;
}

# enum values()/valueOf 보존
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

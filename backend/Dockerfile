# ---- Runtime only: 미리 빌드한 실행 jar 를 그대로 구동 ----
# jar 는 로컬에서 `gradlew :backend:bootJar` 로 만들어 backend/app.jar 로 둔다.
# (jar 는 순수 JVM 바이트코드라 빌드 OS와 무관하게 리눅스에서 실행됨)
# 재현성 위해 변형 태그 고정(권장: 운영 CI 에서 @sha256:... 다이제스트로 추가 고정)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
ENV TZ=Asia/Seoul
# healthcheck 용 curl 설치 + 비-root 사용자 생성 (컨테이너 하드닝)
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 10001 appuser
COPY app.jar app.jar
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

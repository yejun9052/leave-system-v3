# ---- Runtime only: 미리 빌드한 실행 jar 를 그대로 구동 ----
# jar 는 로컬에서 `gradlew :backend:bootJar` 로 만들어 backend/app.jar 로 둔다.
# (jar 는 순수 JVM 바이트코드라 빌드 OS와 무관하게 리눅스에서 실행됨)
# 재현성 위해 변형 태그 고정(권장: 운영 CI 에서 @sha256:... 다이제스트로 추가 고정)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
ENV TZ=Asia/Seoul
# healthcheck 용 curl, DB 백업용 pg_dump(postgresql-client-16) 설치 + 비-root 사용자 생성 (컨테이너 하드닝)
# Ubuntu(jammy) 기본 저장소의 클라이언트는 14 버전이라 DB(16) 백업을 거부한다 → PostgreSQL 공식 저장소(PGDG)에서 설치
# /backups: 백업 폴더(docker-compose.prod.yml 이 서버 폴더를 연결). 주인은 appuser
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && install -d /usr/share/postgresql-common/pgdg \
    && curl -fsSL -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc \
       https://www.postgresql.org/media/keys/ACCC4CF8.asc \
    && echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt jammy-pgdg main" \
       > /etc/apt/sources.list.d/pgdg.list \
    && apt-get update && apt-get install -y --no-install-recommends postgresql-client-16 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 10001 appuser \
    && mkdir -p /backups && chown appuser:appuser /backups
COPY app.jar app.jar
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

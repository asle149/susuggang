# 빌드 단계: JDK + gradlew로 실행 모듈(api)의 bootJar만 굽는다
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew :api:bootJar --no-daemon

# 실행 단계: JRE에 jar만 — 빌드 도구는 최종 이미지에 안 남는다
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

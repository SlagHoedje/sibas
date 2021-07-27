FROM openjdk:11.0.12-oracle
WORKDIR /sibas
COPY . .
ENTRYPOINT ["./gradlew", "run"]

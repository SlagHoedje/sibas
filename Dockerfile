FROM openjdk:11.0.12-oracle
WORKDIR /sibas
COPY . .
RUN chmod +x gradlew
ENTRYPOINT ["/bin/bash", "./gradlew", "run"]

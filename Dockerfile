FROM gradle:7.1.1-jre11
WORKDIR /sibas
COPY --chown=gradle:gradle . .
ENTRYPOINT ["gradle", "run", "--no-daemon"]

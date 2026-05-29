FROM criyle/go-judge:v1.12.0 AS go-judge

FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update -o Acquire::Retries=3 \
    && apt-get install -y --no-install-recommends -o Acquire::Retries=3 \
      g++ \
      python3 \
      wget \
    && mkdir -p /usr/lib/jvm \
    && cp -a /opt/java/openjdk /usr/lib/jvm/temurin-17 \
    && ln -sf /usr/lib/jvm/temurin-17/bin/java /usr/bin/java \
    && ln -sf /usr/lib/jvm/temurin-17/bin/javac /usr/bin/javac \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt
COPY --from=go-judge /opt/go-judge /opt/mount.yaml /opt/

EXPOSE 8090/tcp 5051/tcp 5052/tcp

ENTRYPOINT ["./go-judge"]

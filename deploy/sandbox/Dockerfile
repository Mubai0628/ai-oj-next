FROM criyle/go-judge:v1.12.0 AS go-judge

FROM debian:bookworm-slim

# 用网易 Debian 镜像替换默认 deb.debian.org，
# 避免国内 502 / 拉取慢。先用 HTTP（避免 ca-certificates 鸡生蛋
# 问题），等 ca-certificates 装上后下游可自行升级 HTTPS。
# Debian 12 (bookworm) 默认使用 deb822 格式：/etc/apt/sources.list.d/debian.sources
RUN set -eux; \
    if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i 's|http://deb.debian.org|http://mirrors.163.com|g' /etc/apt/sources.list.d/debian.sources; \
        sed -i 's|http://security.debian.org|http://mirrors.163.com|g' /etc/apt/sources.list.d/debian.sources; \
    fi; \
    if [ -f /etc/apt/sources.list ]; then \
        sed -i 's|http://deb.debian.org|http://mirrors.163.com|g' /etc/apt/sources.list; \
        sed -i 's|http://security.debian.org|http://mirrors.163.com|g' /etc/apt/sources.list; \
    fi

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
      ca-certificates \
      g++ \
      openjdk-17-jdk-headless \
      python3 \
      wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt
COPY --from=go-judge /opt/go-judge /opt/mount.yaml /opt/

EXPOSE 8090/tcp 5051/tcp 5052/tcp

ENTRYPOINT ["./go-judge"]

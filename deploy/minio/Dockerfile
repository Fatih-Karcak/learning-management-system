FROM golang:1.24.2-bookworm AS build
WORKDIR /src
RUN git clone --depth 1 --branch RELEASE.2025-04-22T22-12-26Z https://github.com/minio/minio.git . \
    && test "$(git rev-parse HEAD)" = "0d7408fc9969caf07de6a8c3a84f9fbb10a6739e" \
    && CGO_ENABLED=0 go build -p 2 -trimpath -ldflags='-s -w' -o /minio . \
    && mkdir /empty-data \
    && mkdir -m 1777 /empty-tmp
FROM caddy:2.10.2-alpine AS certificates
FROM scratch
COPY --from=certificates /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=build /minio /minio
COPY --from=build /src/LICENSE /licenses/minio.txt
COPY --from=build --chown=10001:10001 /empty-data/ /data/
COPY --from=build --chown=10001:10001 /empty-tmp/ /tmp/
ENV HOME=/tmp
USER 10001:10001
EXPOSE 9000 9001
ENTRYPOINT ["/minio"]
CMD ["server", "/data", "--console-address", ":9001"]

# --- build ---
FROM golang:alpine AS build
ARG APP_VERSION=dev
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY *.go ./
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w -X main.version=${APP_VERSION}" -o /out/ghesta-server .

# --- run ---
FROM alpine:latest
ARG APP_VERSION=dev
ARG SOURCE_REVISION=unknown
LABEL org.opencontainers.image.title="ghesta-server" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${SOURCE_REVISION}"
# fixed numeric uid so Kubernetes can enforce runAsNonRoot and fsGroup
RUN adduser -D -u 10001 app && mkdir -p /data && chown 10001:10001 /data
USER 10001:10001
COPY --from=build /out/ghesta-server /usr/local/bin/ghesta-server
ENV ADDR=:8080 DB_PATH=/data/ghesta.db
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["ghesta-server"]

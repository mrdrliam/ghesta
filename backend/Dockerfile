# --- build ---
FROM golang:alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY *.go ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/ghesta-server .

# --- run ---
FROM alpine:latest
RUN adduser -D -u 10001 app && mkdir /data && chown app /data
USER app
COPY --from=build /out/ghesta-server /usr/local/bin/ghesta-server
ENV ADDR=:8080 DB_PATH=/data/ghesta.db
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["ghesta-server"]

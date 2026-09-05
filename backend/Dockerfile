# syntax=docker/dockerfile:1
FROM golang:1.27-bookworm AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -ldflags="-s -w -X main.version=${GHI_VERSION:-dev}" -o /out/server ./cmd/server
RUN CGO_ENABLED=0 go build -ldflags="-s -w" -o /out/worker ./cmd/worker

FROM gcr.io/distroless/static-debian12 AS server
COPY --from=build /out/server /server
COPY configs /configs
COPY migrations /migrations
EXPOSE 8080
ENTRYPOINT ["/server", "-config=/configs/config.yaml"]

FROM gcr.io/distroless/static-debian12 AS worker
COPY --from=build /out/worker /worker
COPY configs /configs
ENTRYPOINT ["/worker", "-config=/configs/config.yaml"]

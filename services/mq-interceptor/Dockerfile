FROM golang:1.26 AS builder

ENV CGO_ENABLED=0
ENV GOPROXY=direct

WORKDIR /app
COPY . .

RUN go build -o mq-interceptor ./main.go
RUN echo "nobody:x:65534:65534:nobody:/:" > /app/passwd

# Final stage
FROM gcr.io/distroless/static:nonroot

COPY --from=builder /app/mq-interceptor /
COPY --from=builder /app/passwd /etc/passwd
COPY --from=builder /etc/ssl/certs/ /etc/ssl/certs/

USER 65534

ENTRYPOINT ["/mq-interceptor"]
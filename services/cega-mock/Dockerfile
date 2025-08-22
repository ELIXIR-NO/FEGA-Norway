FROM golang:1.25-alpine AS builder

WORKDIR /app

COPY go.mod ./

RUN go mod tidy
RUN go mod download

COPY main.go .

RUN go build -o /app/cega-mock

FROM alpine:latest

WORKDIR /app

COPY --from=builder /app/cega-mock .

ENV CEGA_USERS_USER=test_instance
ENV CEGA_USERS_PASSWORD=test_password

EXPOSE 8080

CMD ["./cega-mock", "0.0.0.0", "8080"]

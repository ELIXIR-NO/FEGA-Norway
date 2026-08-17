# FEGA-Norway

[![Build and test](https://github.com/ELIXIR-NO/FEGA-Norway/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/ELIXIR-NO/FEGA-Norway/actions/workflows/build-and-test.yml)

Federated EGA Norway: the node that bridges ELIXIR AAI and Central EGA to UiO TSD, so
sensitive data can be submitted, archived and released under Norwegian control.

## Documentation

**https://elixir-no.github.io/FEGA-Norway/**

Architecture, local setup, the end-to-end suites, releases and operations all live
there. This README is deliberately short.

## Running it locally

```sh
./dev.sh start     # build the images and bring the stack up
./gradlew build    # build and test every module
```

## License

[Apache-2.0](LICENSE)

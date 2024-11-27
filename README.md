## Requirements and notes

Needs Eclipse Temurin JDK LTS 21

Use `./mvnw` on UNIX and `./mvnw.cmd` on Windows

Importing into eclipse: File -> Import -> Maven -> Checkout or import from local repo

## Installing maven deps (needed before it can be run)

```sh
./mvnw install
```

## Running tests

```sh
./mvnw test
```

## Execute main

```sh
./mvnw exec:java
```

## Package jar

```sh
./mvnw package
```

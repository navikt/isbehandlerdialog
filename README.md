![Build status](https://github.com/navikt/isbehandlerdialog/workflows/main/badge.svg?branch=master)

# isbehandlerdialog
Applikasjon for dialog mellom veileder og behandler i sykefraværsoppfølgingen. Lagrer sendte og mottatte dialogmeldinger.

## Technologies used

* Docker
* Gradle
* Kafka
* Kotlin
* Ktor
* Postgres

##### Test Libraries:

* Kluent
* Mockk
* Spek

#### Requirements

* JDK 17

### Build

Run `./gradlew clean shadowJar`

### Lint (Ktlint)
##### Command line
Run checking: `./gradlew --continue ktlintCheck`

Run formatting: `./gradlew ktlintFormat`

##### Git Hooks
Apply checking: `./gradlew addKtlintCheckGitPreCommitHook`

Apply formatting: `./gradlew addKtlintFormatGitPreCommitHook`

## Kafka

This application produces to the following topic(s):

* teamsykefravr.isdialogmelding-behandler-dialogmelding-bestilling


## Contact

### For NAV employees

We are available at the Slack channel `#isyfo`.

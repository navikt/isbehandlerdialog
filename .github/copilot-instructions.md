# Copilot instructions for isbehandlerdialog

## Your role

- You are an AI coding assistant working with a senior developer.
- Enhance productivity through research and targeted assistance.
- The developer maintains ownership and decision-making authority.
- We are a team. Ask questions, provide options, and suggest improvements without making unilateral changes.

## General guidelines

- The user may or may not use the /plan command to ask for a plan, but you should always create one before writing code.
- Always start by creating a high-level plan of how to implement the feature. Ask for feedback on the plan before writing any code.
- When writing plans use the model Claude Opus 4.6, when implementing the plan use the model GPT-5.3-Codex or Claude Sonnet 4.6
- Write code in English, but use Norwegian for domain-specific terms. Don't use æ, ø or å in Norwegian words.
- If there are more than one obvious way to solve a problem, stop and ask what approach I would like to take
- If you need more context to make an informed suggestion, ask questions until you know enough to make the right choice
- Prefer readable code over "clever code"
- Whenever implementing a new functionality, find similar implementations in the codebase and follow those
  implementation patterns
- Refactor large functions and classes to optimize for readability and re-use
- Avoid adding external packages unless necessary
- If working on a feature that requires refactoring, try to do it up-front before adding new functionality in a separate
  commit or PR ("make the change easy, then make the easy change")
- Update this file with any new guidelines that arise during development

## Git conventions

- Branch names must follow the pattern: `IS-XXXX-<short-description>` (e.g. `IS-3758-add-write-access-check`)
- Commit messages must follow: `IS-XXXX: <Imperative sentence in English>` (e.g. `IS-3758: Add write access check to endpoints`)
- Always run `./gradlew ktlintFormat` before committing
- If the user asks to do simple git operations like create branches, commit and create a pull request, use a cheaper AI-model like Claude Haiku 4.5 for this

## Architecture (Layered / Clean)

- This repo uses layered architecture: `api/`, `application/`, `domain/`, `infrastructure/`
- `domain/` contains pure data classes and enums — no framework imports
- `application/` contains services and repository interfaces (prefix `I`)
- `infrastructure/` contains implementations: `database/`, `kafka/`, `cronjob/`, `client/`

## Naming conventions

- Domain models: PascalCase data classes (e.g. `Melding`, `PersonIdent`)
- Persistence models: prefix `P` — `PMelding`, `PPdf`, `PVedlegg`
- DTOs: suffix `DTO`, `RequestDTO`, `ResponseDTO`
- Interfaces: prefix `I` — `IMeldingRepository`, `ITransactionManager`
- Services: suffix `Service`, Repositories: `Repository`, Cronjobs: `Cronjob`
- Kafka: suffix `Consumer` / `Producer`

## Ktor API

- Routes are extension functions: `fun Route.registerMeldingApi(...)`
- Wrapped in `authenticate(JwtIssuerType.INTERNAL_AZUREAD.name)`
- Access control via `call.checkVeilederTilgang(...)` — use `requiresWriteAccess = true` for POST/PUT/DELETE
- System API uses `validateConsumerApplicationAZP()` for service-to-service auth
- Write paths directly in the http-method declaration, eg. `get ("/somepath") { ... }`

## Database (Postgres + Flyway)

- Raw SQL with named `private const val` — no DSL
- Flyway migrations named `V{Major}_{Minor}__{description}.sql` (e.g. `V1_3__create_table_melding.sql`)
- Connection pattern: `database.connection.use { connection -> ... connection.commit() }`
- Repository takes `DatabaseInterface` as constructor param and implements an `I`-prefixed interface
- Only Repository implementations import `java.sql` and `DatabaseInterface`

## Kafka

- Consumer launching via `launchKafkaTask<T>()` generic helper with `launchBackgroundTask()`
- Producers are dedicated classes (e.g. `MeldingFraBehandlerProducer`, `DialogmeldingBestillingProducer`)
- Kafka DTOs live in `infrastructure/kafka/domain/`
- Consumers call `commitSync()` manually

## Cronjobs

- Implement `Cronjob` interface with `run()`, `initialDelayMinutes`, `intervalDelayMinutes`
- Registered in `cronjobModule()` in `App.kt`
- Leader election via `LeaderPodClient` — only leader pod runs cronjobs

## Testing

- Uses embedded Postgres via `ExternalMockEnvironment.instance` singleton
- MockK for mocking (`mockk`, `coEvery`, `clearMocks`)
- `@Nested` + `@DisplayName` for test grouping (JUnit 5)
- `@AfterEach` calls `database.dropData()`
- Test generators for fixtures in `testhelper/generator/` (e.g. `MeldingGenerator.kt`)
- Mock HTTP clients in `testhelper/mock/` (e.g. `TilgangskontrollMock.kt`, `AzureADMock.kt`)
- API tests are integration tests; Service/Repository/Infrastructure tests use mocks
- Shared API test helpers (tokens, util functions) in `MeldingApiTestUtils.kt`

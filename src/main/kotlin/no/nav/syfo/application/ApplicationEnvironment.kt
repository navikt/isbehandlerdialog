package no.nav.syfo.application

import io.ktor.server.application.*
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.*
import no.nav.syfo.client.azuread.AzureEnvironment

const val NAIS_DATABASE_ENV_PREFIX = "NAIS_DATABASE_ISBEHANDLERDIALOG_ISBEHANDLERDIALOG_DB"

data class Environment(
    val database: DatabaseEnvironment = DatabaseEnvironment(
        host = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_HOST"),
        port = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PORT"),
        name = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_DATABASE"),
        username = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_USERNAME"),
        password = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PASSWORD"),
    ),
    val azure: AzureEnvironment = AzureEnvironment(
        appClientId = getEnvVar("AZURE_APP_CLIENT_ID"),
        appClientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
        appPreAuthorizedApps = getEnvVar("AZURE_APP_PRE_AUTHORIZED_APPS"),
        appWellKnownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
        openidConfigTokenEndpoint = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    ),
    val kafka: KafkaEnvironment = KafkaEnvironment(
        aivenBootstrapServers = getEnvVar("KAFKA_BROKERS"),
        aivenCredstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
        aivenKeystoreLocation = getEnvVar("KAFKA_KEYSTORE_PATH"),
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
        aivenSchemaRegistryUrl = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
        aivenRegistryUser = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
        aivenRegistryPassword = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
    ),
    val electorPath: String = getEnvVar("ELECTOR_PATH"),
    val clients: ClientsEnvironment = ClientsEnvironment(
        padm2 = ClientEnvironment(
            baseUrl = getEnvVar("PADM2_URL"),
            clientId = getEnvVar("PADM2_CLIENT_ID"),
        ),
        istilgangskontroll = ClientEnvironment(
            baseUrl = getEnvVar("ISTILGANGSKONTROLL_URL"),
            clientId = getEnvVar("ISTILGANGSKONTROLL_CLIENT_ID"),
        ),
        dialogmeldingpdfgen = OpenClientEnvironment(
            baseUrl = "http://ispdfgen",
        ),
        legeerklaringpdfgen = OpenClientEnvironment(
            baseUrl = "http://pale-2-pdfgen.teamsykmelding",
        ),
        dokarkiv = ClientEnvironment(
            baseUrl = getEnvVar("DOKARKIV_URL"),
            clientId = getEnvVar("DOKARKIV_CLIENT_ID"),
        ),
        oppfolgingstilfelle = ClientEnvironment(
            baseUrl = getEnvVar("ISOPPFOLGINGSTILFELLE_URL"),
            clientId = getEnvVar("ISOPPFOLGINGSTILFELLE_CLIENT_ID"),
        ),
    ),
    val cronjobUbesvartMeldingIntervalDelayMinutes: Long = getEnvVar("CRONJOB_UBESVART_MELDING_INTERVAL_DELAY_MINUTES").toLong(),
    val cronjobUbesvartMeldingFristHours: Long = getEnvVar("CRONJOB_UBESVART_MELDING_FRIST_HOURS").toLong(),
    val legeerklaringBucketName: String = getEnvVar("LEGEERKLARING_BUCKET_NAME"),
    val legeerklaringVedleggBucketName: String = getEnvVar("LEGEERKLARING_VEDLEGG_BUCKET_NAME"),
    val cronjobAvvistMeldingStatusIntervalDelayMinutes: Long = getEnvVar("CRONJOB_AVVIST_MELDING_STATUS_INTERVAL_DELAY_MINUTES").toLong(),
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

val Application.envKind get() = environment.config.property("ktor.environment").getString()

fun Application.isDev(block: () -> Unit) {
    if (envKind == "dev") block()
}

fun Application.isProd(block: () -> Unit) {
    if (envKind == "production") block()
}

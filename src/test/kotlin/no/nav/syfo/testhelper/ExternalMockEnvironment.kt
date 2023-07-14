package no.nav.syfo.testhelper

import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.wellknown.WellKnown
import no.nav.syfo.testhelper.mock.*
import java.nio.file.Paths

fun wellKnownInternalAzureAD(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        issuer = "https://sts.issuer.net/veileder/v2",
        jwksUri = uri.toString()
    )
}

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()

    val embeddedEnvironment: KafkaEnvironment = testKafka()
    val environment = testEnvironment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL
    )

    val mockHttpClient = mockHttpClient(environment = environment)

    val wellKnownInternalAzureAD = wellKnownInternalAzureAD()

    companion object {
        val instance: ExternalMockEnvironment = ExternalMockEnvironment()
    }
}

fun ExternalMockEnvironment.startExternalMocks() {
    this.embeddedEnvironment.start()
}

fun ExternalMockEnvironment.stopExternalMocks() {
    this.database.stop()
    this.embeddedEnvironment.tearDown()
}

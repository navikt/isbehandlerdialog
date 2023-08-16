package no.nav.syfo.client

data class ClientsEnvironment(
    val padm2: ClientEnvironment,
    val syfotilgangskontroll: ClientEnvironment,
    val dialogmeldingpdfgen: OpenClientEnvironment,
    val legeerklaringpdfgen: OpenClientEnvironment,
    val dokarkiv: ClientEnvironment,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)

data class OpenClientEnvironment(
    val baseUrl: String,
)

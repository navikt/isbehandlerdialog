package no.nav.syfo.domain

data class PartnerId(val value: Int) {
    override fun toString(): String {
        return value.toString()
    }
}

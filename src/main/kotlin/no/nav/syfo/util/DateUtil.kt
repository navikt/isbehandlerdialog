package no.nav.syfo.util

import java.time.*
import java.time.temporal.ChronoUnit

val defaultZoneOffset: ZoneOffset = ZoneOffset.UTC

fun nowUTC(): OffsetDateTime = OffsetDateTime.now(defaultZoneOffset)

fun tomorrow(): LocalDate = LocalDate.now().plusDays(1)

fun OffsetDateTime.millisekundOpplosning(): OffsetDateTime = this.truncatedTo(ChronoUnit.MILLIS)

fun LocalDateTime.toOffsetDateTime(): OffsetDateTime = this.atZone(ZoneId.of("Europe/Oslo")).toOffsetDateTime()

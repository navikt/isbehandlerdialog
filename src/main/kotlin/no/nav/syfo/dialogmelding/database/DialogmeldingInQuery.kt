package no.nav.syfo.dialogmelding.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmelding.domain.DialogmeldingIn
import no.nav.syfo.dialogmelding.domain.DialogmeldingType
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.UUID

const val queryGetDialogmeldingInForArbeidstakerPersonident =
    """
        SELECT *
        FROM DIALOGMELDING_IN
        WHERE arbeidstaker_personident = ?
    """

fun DatabaseInterface.getDialogmeldingInForArbeidstakerPersonident(
    arbeidstakerPersonident: PersonIdent
): List<DialogmeldingIn> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetDialogmeldingInForArbeidstakerPersonident).use {
            it.setString(1, arbeidstakerPersonident.value)
            it.executeQuery().toList { toDialogmeldingIn() }
        }
    }
}

const val queryCreateDialogmeldingIn =
    """
        INSERT INTO DIALOGMELDING_IN ( 
            id,
            uuid,
            created_at,
            msg_id,
            msg_type,
            mottak_id,
            conversation_ref,
            parent_ref,
            mottatt_tidspunkt,
            arbeidstaker_personident,
            behandler_personident,
            behandler_hpr_id,
            legekontor_org_nr,
            legekontor_her_id,
            legekontor_navn,
            tekst_notat_innhold,
            antall_vedlegg
        ) VALUES(DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
    """

const val queryCreateDialogmeldingInFellesformat =
    """
        INSERT INTO DIALOGMELDING_IN_FELLESFORMAT (
            id,
            dialogmelding_in_id,
            fellesformat
        ) VALUES (DEFAULT, ?, ?) RETURNING id
    """

fun Connection.createNewDialogmeldingIn(
    dialogmeldingIn: DialogmeldingIn,
    fellesformat: String,
    commit: Boolean = true,
): Int {
    val idList = this.prepareStatement(queryCreateDialogmeldingIn).use {
        it.setString(1, dialogmeldingIn.uuid.toString())
        it.setObject(2, OffsetDateTime.now())
        it.setString(3, dialogmeldingIn.msgId)
        it.setString(4, dialogmeldingIn.msgType.name)
        it.setString(5, dialogmeldingIn.mottakId)
        it.setString(6, dialogmeldingIn.conversationRef.toString())
        it.setString(7, dialogmeldingIn.parentRef.toString())
        it.setObject(8, dialogmeldingIn.mottattTidspunkt)
        it.setString(9, dialogmeldingIn.arbeidstakerPersonIdent.value)
        it.setString(10, dialogmeldingIn.behandlerPersonIdent?.value)
        it.setString(11, dialogmeldingIn.behandlerHprId)
        it.setString(12, dialogmeldingIn.legekontorOrgnr?.value)
        it.setString(13, dialogmeldingIn.legekontorHerId)
        it.setString(14, dialogmeldingIn.legekontorNavn)
        it.setString(15, dialogmeldingIn.tekstNotatInnhold)
        it.setInt(16, dialogmeldingIn.antallVedlegg)
        it.executeQuery().toList { getInt("id") }
    }
    if (idList.size != 1) {
        throw SQLException("Creating DialogmeldingIn failed, no rows affected.")
    }
    val id = idList.first()
    this.prepareStatement(queryCreateDialogmeldingInFellesformat).use {
        it.setInt(1, id)
        it.setString(2, fellesformat)
        it.executeQuery()
    }
    if (commit) {
        this.commit()
    }
    return id
}

fun ResultSet.toDialogmeldingIn() =
    DialogmeldingIn(
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        msgId = getString("msg_id"),
        msgType = DialogmeldingType.valueOf(getString("msg_type")),
        mottakId = getString("mottak_id"),
        conversationRef = getString("conversationRef")?.let { UUID.fromString(it) },
        parentRef = getString("parentRef")?.let { UUID.fromString(it) },
        mottattTidspunkt = getObject("mottatt_tidspunkt", OffsetDateTime::class.java),
        arbeidstakerPersonIdent = PersonIdent(getString("arbeidstaker_personident")),
        behandlerPersonIdent = getString("behandler_person_ident")?.let { PersonIdent(it) },
        behandlerHprId = getString("behandler_hpr_id"),
        legekontorOrgnr = getString("legekontor_orgnr")?.let { Virksomhetsnummer(it) },
        legekontorHerId = getString("legekontor_her_id"),
        legekontorNavn = getString("legekontor_navn"),
        tekstNotatInnhold = getString("tekst_notat_innhold"),
        antallVedlegg = getInt("antall_vedlegg"),
    )

package no.nav.syfo.application

import java.sql.Connection

interface ITransactionManager {
    fun <T> run(block: (transaction: ITransaction) -> T): T
}

interface ITransaction {
    val connection: Connection
}

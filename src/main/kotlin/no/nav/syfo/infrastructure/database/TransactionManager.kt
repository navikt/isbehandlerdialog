package no.nav.syfo.infrastructure.database

import no.nav.syfo.application.ITransaction
import no.nav.syfo.application.ITransactionManager
import java.sql.Connection

data class DatabaseTransaction(override val connection: Connection) : ITransaction

class TransactionManager(private val database: DatabaseInterface) : ITransactionManager {
    override fun <T> run(block: (transaction: ITransaction) -> T): T =
        database.connection.use { connection ->
            val result = block(DatabaseTransaction(connection))
            connection.commit()
            result
        }
}

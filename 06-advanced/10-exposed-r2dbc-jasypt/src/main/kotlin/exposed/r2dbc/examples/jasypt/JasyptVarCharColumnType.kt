package exposed.r2dbc.examples.jasypt

import io.bluetape4k.crypto.encrypt.Encryptor
import org.jetbrains.exposed.v1.core.ColumnTransformer
import org.jetbrains.exposed.v1.core.ColumnWithTransform
import org.jetbrains.exposed.v1.core.VarCharColumnType

class JasyptVarCharColumnType(
    private val encryptor: Encryptor,
    colLength: Int,
): ColumnWithTransform<String, String>(VarCharColumnType(colLength), JasyptStringEncryptionTransformer(encryptor))

class JasyptStringEncryptionTransformer(
    private val encryptor: Encryptor,
): ColumnTransformer<String, String> {
    /**
     * Encrypts the given value using the provided [encryptor].
     *
     * @param value The value to encrypt.
     * @return The encrypted value.
     */
    override fun unwrap(value: String) = encryptor.encrypt(value)

    /**
     * Decrypts the given value using the provided [encryptor].
     */
    override fun wrap(value: String) = encryptor.decrypt(value)
}

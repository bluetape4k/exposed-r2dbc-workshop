package exposed.r2dbc.examples.jasypt

import io.bluetape4k.crypto.encrypt.Encryptor
import org.jetbrains.exposed.v1.core.BinaryColumnType
import org.jetbrains.exposed.v1.core.ColumnTransformer
import org.jetbrains.exposed.v1.core.ColumnWithTransform

class JasyptBinaryColumnType(
    private val encryptor: Encryptor,
    length: Int,
): ColumnWithTransform<ByteArray, ByteArray>(BinaryColumnType(length), JasyptByteArrayEncryptionTransformer(encryptor))

class JasyptByteArrayEncryptionTransformer(
    private val encryptor: Encryptor,
): ColumnTransformer<ByteArray, ByteArray> {
    /**
     * Encrypts the given value using the provided [encryptor].
     *
     * @param value The value to encrypt.
     * @return The encrypted value.
     */
    override fun unwrap(value: ByteArray) = encryptor.encrypt(value)

    /**
     * Decrypts the given value using the provided [encryptor].
     */
    override fun wrap(value: ByteArray) = encryptor.decrypt(value)
}

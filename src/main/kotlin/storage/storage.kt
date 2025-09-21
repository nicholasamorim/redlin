package org.redlin.storage

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.redlin.commands.SetStatus

@OptIn(ExperimentalTime::class) fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

data class StorageData(
    val value: ByteArray,
    val creationTime: Long = nowMs(),
    val expiry: Long? = null, // millis
) {

    /** Returns true if this entry has an expiry and is now expired. */
    fun isExpired(nowMillis: Long = nowMs()): Boolean {
        val e = expiry ?: return false
        return nowMillis - creationTime >= e
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StorageData

        if (creationTime != other.creationTime) return false
        if (expiry != other.expiry) return false
        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = creationTime.hashCode()
        result = 31 * result + (expiry?.hashCode() ?: 0)
        result = 31 * result + value.contentHashCode()
        return result
    }
}

sealed class StorageResult<out T> {
    data class Ok<T>(val value: T) : StorageResult<T>()

    object NotFound : StorageResult<Nothing>()

    data class Error(val message: String) : StorageResult<Nothing>()
}

enum class WriteMode {
    UPSERT,
    ONLY_IF_ABSENT,
    ONLY_IF_PRESENT,
}

interface StorageManager {
    fun set(
        key: String,
        value: ByteArray,
        expiryMs: Long? = null,
        mode: WriteMode = WriteMode.UPSERT,
    ): StorageResult<SetStatus>

    fun get(key: String): StorageResult<ByteArray>
}

class InMemoryStorageManager : StorageManager {
    private val store = ConcurrentHashMap<String, StorageData>()
    private val expiry = ConcurrentHashMap<String, Long>()

    override fun set(
        key: String,
        value: ByteArray,
        expiryMs: Long?,
        mode: WriteMode,
    ): StorageResult<SetStatus> {
        val now = System.currentTimeMillis()
        var status = SetStatus.NOT_WRITTEN

        store.compute(key) { _, existing ->
            val current = existing?.takeUnless { it.isExpired(now) }

            when (mode) {
                WriteMode.UPSERT -> {
                    status = SetStatus.WRITTEN
                    StorageData(value, creationTime = now, expiry = expiryMs)
                }
                WriteMode.ONLY_IF_ABSENT -> {
                    if (current == null) {
                        status = SetStatus.WRITTEN
                        StorageData(value, creationTime = now, expiry = expiryMs)
                    } else {
                        current
                    }
                }
                WriteMode.ONLY_IF_PRESENT -> {
                    if (current != null) {
                        status = SetStatus.WRITTEN
                        StorageData(value, creationTime = now, expiry = expiryMs)
                    } else {
                        // absent or expired
                        null
                    }
                }
            }
        }

        return StorageResult.Ok(status)
    }

    override fun get(key: String): StorageResult<ByteArray> {
        val data = store[key] ?: return StorageResult.NotFound
        if (data.isExpired()) {
            store.remove(key) // lazy eviction
            return StorageResult.NotFound
        }
        return StorageResult.Ok(data.value)
    }
}

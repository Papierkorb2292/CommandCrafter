package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.internal.bind.JsonTreeReader
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.DecoderException
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import org.eclipse.lsp4j.jsonrpc.json.adapters.TypeUtils

class FileSystemResult<out TResultType> private constructor(
    val type: ResultType,
    val result: TResultType?,
    val fileNotFoundError: FileNotFoundError?,
) {
    constructor(result: TResultType) : this(ResultType.SUCCESS, result, null)
    constructor(error: FileNotFoundError) : this(ResultType.FILE_NOT_FOUND_ERROR, null, error)

    companion object {
        fun <TResultType> createCodec(resultCodec: PacketCodec<ByteBuf, TResultType>): PacketCodec<PacketByteBuf, FileSystemResult<TResultType>> {
            return object : PacketCodec<PacketByteBuf, FileSystemResult<TResultType>> {
                override fun decode(buf: PacketByteBuf): FileSystemResult<TResultType> {
                    val typeOrdinal = buf.readByte().toInt()
                    if(typeOrdinal < 0 || typeOrdinal >= ResultType.entries.size)
                        throw DecoderException("Unknown FileSystemResult type ordinal: $typeOrdinal")
                    return when(ResultType.entries[typeOrdinal]) {
                        ResultType.SUCCESS -> FileSystemResult(resultCodec.decode(buf))
                        ResultType.FILE_NOT_FOUND_ERROR -> FileSystemResult(FileNotFoundError(buf.readString()))
                    }
                }

                override fun encode(buf: PacketByteBuf, value: FileSystemResult<TResultType>) {
                    buf.writeByte(value.type.ordinal)
                    when(value.type) {
                        ResultType.SUCCESS -> resultCodec.encode(buf, value.result)
                        ResultType.FILE_NOT_FOUND_ERROR -> buf.writeString(value.fileNotFoundError!!.fileNotFoundErrorMessage)
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <TNewResultType> map(
        resultMapper: (TResultType) -> TNewResultType,
        fileNotFoundMapper: (FileNotFoundError) -> FileNotFoundError = { it },
    ): FileSystemResult<TNewResultType> = when(type) {
        ResultType.SUCCESS -> FileSystemResult(resultMapper(result as TResultType))
        ResultType.FILE_NOT_FOUND_ERROR -> FileSystemResult(fileNotFoundMapper(fileNotFoundError!!))
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <TNewResultType> flatMap(
        resultMapper: (TResultType) -> FileSystemResult<TNewResultType>,
        fileNotFoundMapper: (FileNotFoundError) -> FileSystemResult<TNewResultType> = { FileSystemResult(it) },
    ): FileSystemResult<TNewResultType> = when(type) {
        ResultType.SUCCESS -> resultMapper(result as TResultType)
        ResultType.FILE_NOT_FOUND_ERROR -> fileNotFoundMapper(fileNotFoundError!!)
    }

    inline fun handleErrorAndGetResult(block: (FileSystemResult<Nothing>) -> Nothing): TResultType {
        if(type != ResultType.SUCCESS)
            @Suppress("UNCHECKED_CAST")
            block(this as FileSystemResult<Nothing>)
        @Suppress("UNCHECKED_CAST")
        return result as TResultType
    }

    object TypeAdapterFactory : com.google.gson.TypeAdapterFactory {
        override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
            if(type.rawType != FileSystemResult::class.java)
                return null
            val resultType = TypeUtils.getElementTypes(type, FileSystemResult::class.java)[0]
            val resultTypeAdapter = gson.getAdapter(TypeToken.get(resultType))
            return object : TypeAdapter<T>() {
                override fun write(out: JsonWriter, value: T) {
                    val fileSystemResult = value as FileSystemResult<*>
                    when(fileSystemResult.type) {
                        ResultType.SUCCESS -> writeResult(resultTypeAdapter, out, fileSystemResult.result)
                        ResultType.FILE_NOT_FOUND_ERROR -> {
                            out.beginObject()
                            out.name("fileNotFoundErrorMessage")
                                .value(fileSystemResult.fileNotFoundError!!.fileNotFoundErrorMessage)
                            out.endObject()
                        }
                    }
                }

                override fun read(`in`: JsonReader): T {
                    val jsonTree = gson.toJsonTree(`in`)
                    if(jsonTree.isJsonObject && jsonTree.asJsonObject.has("fileNotFoundErrorMessage")) {
                        @Suppress("UNCHECKED_CAST")
                        return FileSystemResult<Nothing>(FileNotFoundError(jsonTree.asJsonObject.get("fileNotFoundErrorMessage").asString)) as T
                    }
                    @Suppress("UNCHECKED_CAST")
                    return FileSystemResult<Any?>(resultTypeAdapter.read(JsonTreeReader(jsonTree))) as T
                }

                // Make kotlin happy with the types of the result and the type adapter
                private fun <TAdapterType> writeResult(adapter: TypeAdapter<TAdapterType>, out: JsonWriter, value: Any?) {
                    @Suppress("UNCHECKED_CAST")
                    adapter.write(out, value as TAdapterType)
                }
            }

        }
    }

    enum class ResultType {
        SUCCESS,
        FILE_NOT_FOUND_ERROR,
    }
}

class FileNotFoundError(val fileNotFoundErrorMessage: String)
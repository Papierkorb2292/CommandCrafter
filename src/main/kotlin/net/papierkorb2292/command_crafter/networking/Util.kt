package net.papierkorb2292.command_crafter.networking

import com.google.gson.Gson
import com.google.gson.JsonElement
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.*
import kotlin.jvm.optionals.getOrNull

fun <B : ByteBuf, V: Any> PacketCodec<B, V>.nullable(): PacketCodec<B, V?> =
    PacketCodecs.optional(this).xmap(Optional<V>::getOrNull) { Optional.ofNullable(it) }
fun <B : ByteBuf, V> PacketCodec<B, V>.list(): PacketCodec<B, List<V>> =
    PacketCodecs.collection(::ArrayList, this)
inline fun <B : ByteBuf, reified V> PacketCodec<B, V>.array(): PacketCodec<B, Array<V>> = list().xmap(
    List<V>::toTypedArray,
    Array<V>::toList
)
infix fun <B : ByteBuf, V1, V2> PacketCodec<B, V1>.makeEither(other: PacketCodec<B, V2>) = object : PacketCodec<B, Either<V1, V2>> {
    override fun decode(byteBuf: B): Either<V1, V2> {
        return if(byteBuf.readBoolean()) Either.forLeft(this@makeEither.decode(byteBuf))
        else Either.forRight(other.decode(byteBuf))
    }

    override fun encode(byteBuf: B, either: Either<V1, V2>) {
        either.map(
            {
                byteBuf.writeBoolean(true)
                this@makeEither.encode(byteBuf, it)
            },
            {
                byteBuf.writeBoolean(false)
                other.encode(byteBuf, it)
            }
        )
    }
}

fun <T: Enum<T>> enumConstantCodec(enumClass: Class<T>): PacketCodec<ByteBuf, T> {
    val values = enumClass.enumConstants
    return PacketCodecs.VAR_INT.xmap(values::get) { it.ordinal }
}

val UNIT_CODEC = PacketCodec.unit<ByteBuf, Unit>(Unit)

val NULLABLE_STRING_PACKET_CODEC = PacketCodecs.STRING.nullable()
val NULLABLE_BOOL_PACKET_CODEC = PacketCodecs.BOOLEAN.nullable()
val NULLABLE_VAR_INT_PACKET_CODEC = PacketCodecs.VAR_INT.nullable()

val POSITION_PACKET_CODEC: PacketCodec<ByteBuf, Position> = PacketCodec.tuple(
    PacketCodecs.VAR_INT,
    Position::getLine,
    PacketCodecs.VAR_INT,
    Position::getCharacter,
    ::Position
)

val RANGE_PACKET_CODEC: PacketCodec<ByteBuf, Range> = PacketCodec.tuple(
    POSITION_PACKET_CODEC,
    Range::getStart,
    POSITION_PACKET_CODEC,
    Range::getEnd,
    ::Range
)

val OBJECT_PACKET_CODEC_GSON = Gson()
val OBJECT_PACKET_CODEC: PacketCodec<ByteBuf, Any> = PacketCodecs.STRING.xmap<Any>(
    { OBJECT_PACKET_CODEC_GSON.fromJson(it, JsonElement::class.java) },
    { OBJECT_PACKET_CODEC_GSON.toJson(it) }
)
val NULLABLE_OBJECT_PACKET_CODEC = OBJECT_PACKET_CODEC.nullable()

val SOURCE_PACKET_CODEC: PacketCodec<ByteBuf, Source> = PacketCodec.recursive { self ->
    object : PacketCodec<ByteBuf, Source> {
        val CHILDREN_CODEC = self.array().nullable()
        val PRESENTATION_HINT_CODEC = enumConstantCodec(SourcePresentationHint::class.java).nullable()

        override fun decode(buf: ByteBuf) = Source().apply {
            name = PacketCodecs.STRING.decode(buf)
            path = NULLABLE_STRING_PACKET_CODEC.decode(buf)
            sourceReference = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
            presentationHint = PRESENTATION_HINT_CODEC.decode(buf)
            origin = NULLABLE_STRING_PACKET_CODEC.decode(buf)
            sources = CHILDREN_CODEC.decode(buf)
        }

        override fun encode(buf: ByteBuf, value: Source) {
            PacketCodecs.STRING.encode(buf, value.name)
            NULLABLE_STRING_PACKET_CODEC.encode(buf, value.path)
            NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.sourceReference)
            PRESENTATION_HINT_CODEC.encode(buf, value.presentationHint)
            NULLABLE_STRING_PACKET_CODEC.encode(buf, value.origin)
            CHILDREN_CODEC.encode(buf, value.sources)
        }
    }
}
val NULLABLE_SOURCE_CODEC = SOURCE_PACKET_CODEC.nullable()

val BREAKPOINT_PACKET_CODEC = object : PacketCodec<ByteBuf, Breakpoint> {
    override fun decode(buf: ByteBuf) = Breakpoint().apply {
        id = PacketCodecs.VAR_INT.decode(buf)
        isVerified = PacketCodecs.BOOLEAN.decode(buf)
        message = NULLABLE_STRING_PACKET_CODEC.decode(buf)
        source = NULLABLE_SOURCE_CODEC.decode(buf)
        line = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
        column = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
        endLine = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
        endColumn = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
    }

    override fun encode(buf: ByteBuf, value: Breakpoint) {
        PacketCodecs.VAR_INT.encode(buf, value.id)
        PacketCodecs.BOOLEAN.encode(buf, value.isVerified)
        NULLABLE_STRING_PACKET_CODEC.encode(buf, value.message)
        NULLABLE_SOURCE_CODEC.encode(buf, value.source)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.line)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.column)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.endLine)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.endColumn)
    }
}

val SOURCE_BREAKPOINT_PACKET_CODEC: PacketCodec<ByteBuf, SourceBreakpoint> = PacketCodec.tuple(
    PacketCodecs.VAR_INT,
    SourceBreakpoint::getLine,
    NULLABLE_VAR_INT_PACKET_CODEC,
    SourceBreakpoint::getColumn,
    NULLABLE_STRING_PACKET_CODEC,
    SourceBreakpoint::getCondition,
    NULLABLE_STRING_PACKET_CODEC,
    SourceBreakpoint::getHitCondition,
    NULLABLE_STRING_PACKET_CODEC,
    SourceBreakpoint::getLogMessage
) { line, column, condition, hitCondition, logMessage ->
    SourceBreakpoint().apply {
        this.line = line
        this.column = column
        this.condition = condition
        this.hitCondition = hitCondition
        this.logMessage = logMessage
    }
}

val UNPARSED_BREAKPOINT_PACKET_CODEC: PacketCodec<ByteBuf, UnparsedServerBreakpoint> = PacketCodec.tuple(
    PacketCodecs.VAR_INT,
    UnparsedServerBreakpoint::id,
    NULLABLE_VAR_INT_PACKET_CODEC,
    UnparsedServerBreakpoint::sourceReference,
    SOURCE_BREAKPOINT_PACKET_CODEC,
    UnparsedServerBreakpoint::sourceBreakpoint
) { id, sourceReference, sourceBreakpoint ->
    UnparsedServerBreakpoint(id, sourceReference, sourceBreakpoint)
}

val SOURCE_RESPONSE_PACKET_CODEC: PacketCodec<ByteBuf, SourceResponse> = PacketCodec.tuple(
    PacketCodecs.STRING,
    SourceResponse::getContent,
    NULLABLE_STRING_PACKET_CODEC,
    SourceResponse::getMimeType
) { content, mimeType ->
    SourceResponse().apply {
        this.content = content
        this.mimeType = mimeType
    }
}

val SCOPE_PACKET_CODEC = object : PacketCodec<ByteBuf, Scope> {
    override fun decode(buf: ByteBuf) = Scope().apply {
        name = PacketCodecs.STRING.decode(buf)
        presentationHint = NULLABLE_STRING_PACKET_CODEC.decode(buf)
        variablesReference = PacketCodecs.VAR_INT.decode(buf)
        namedVariables = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
        indexedVariables = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
        isExpensive = PacketCodecs.BOOLEAN.decode(buf)
        source = NULLABLE_SOURCE_CODEC.decode(buf)
        line = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
        column = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
        endLine = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
        endColumn = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
    }

    override fun encode(buf: ByteBuf, value: Scope) {
        PacketCodecs.STRING.encode(buf, value.name)
        NULLABLE_STRING_PACKET_CODEC.encode(buf, value.presentationHint)
        PacketCodecs.VAR_INT.encode(buf, value.variablesReference)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.namedVariables)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.indexedVariables)
        PacketCodecs.BOOLEAN.encode(buf, value.isExpensive)
        NULLABLE_SOURCE_CODEC.encode(buf, value.source)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.line)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.column)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.endLine)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.endColumn)
    }
}

val STOPPED_EVENT_ARGUMENTS_PACKET_CODEC = object : PacketCodec<ByteBuf, StoppedEventArguments> {
    val HIT_BREAKPOINT_IDS_CODEC = PacketCodecs.VAR_INT.array().nullable()
    override fun decode(buf: ByteBuf) = StoppedEventArguments().apply {
        reason = PacketCodecs.STRING.decode(buf)
        description = NULLABLE_STRING_PACKET_CODEC.decode(buf)
        allThreadsStopped = NULLABLE_BOOL_PACKET_CODEC.decode(buf)
        text = NULLABLE_STRING_PACKET_CODEC.decode(buf)
        threadId = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
        hitBreakpointIds = HIT_BREAKPOINT_IDS_CODEC.decode(buf)
        preserveFocusHint = NULLABLE_BOOL_PACKET_CODEC.decode(buf)
    }

    override fun encode(buf: ByteBuf, value: StoppedEventArguments) {
        PacketCodecs.STRING.encode(buf, value.reason)
        NULLABLE_STRING_PACKET_CODEC.encode(buf, value.description)
        NULLABLE_BOOL_PACKET_CODEC.encode(buf, value.allThreadsStopped)
        NULLABLE_STRING_PACKET_CODEC.encode(buf, value.text)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.threadId)
        HIT_BREAKPOINT_IDS_CODEC.encode(buf, value.hitBreakpointIds)
        NULLABLE_BOOL_PACKET_CODEC.encode(buf, value.preserveFocusHint)
    }
}

val BREAKPOINT_EVENT_ARGUMENTS_PACKET_CODEC: PacketCodec<ByteBuf, BreakpointEventArguments> = PacketCodec.tuple(
    PacketCodecs.STRING,
    BreakpointEventArguments::getReason,
    BREAKPOINT_PACKET_CODEC,
    BreakpointEventArguments::getBreakpoint
) { breakpointId, breakpointData ->
    BreakpointEventArguments().apply {
        reason = breakpointId
        breakpoint = breakpointData
    }
}

val OUTPUT_EVENT_ARGUMENTS_PACKET_CODEC = object : PacketCodec<ByteBuf, OutputEventArguments> {
    val GROUP_CODEC = enumConstantCodec(OutputEventArgumentsGroup::class.java).nullable()

    override fun decode(buf: ByteBuf) = OutputEventArguments().apply {
        category = NULLABLE_STRING_PACKET_CODEC.decode(buf)
        output = PacketCodecs.STRING.decode(buf)
        data = NULLABLE_OBJECT_PACKET_CODEC.decode(buf)
        source = NULLABLE_SOURCE_CODEC.decode(buf)
        line = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
        column = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
        variablesReference = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
        group = GROUP_CODEC.decode(buf)
    }

    override fun encode(buf: ByteBuf, value: OutputEventArguments) {
        NULLABLE_STRING_PACKET_CODEC.encode(buf, value.category)
        PacketCodecs.STRING.encode(buf, value.output)
        NULLABLE_OBJECT_PACKET_CODEC.encode(buf, value.data)
        NULLABLE_SOURCE_CODEC.encode(buf, value.source)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.line)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.column)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.variablesReference)
        GROUP_CODEC.encode(buf, value.group)
    }
}

val EXITED_EVENT_ARGUMENTS_PACKET_CODEC: PacketCodec<ByteBuf, ExitedEventArguments> = PacketCodecs.VAR_INT.xmap(
    { ExitedEventArguments().apply { exitCode = it }},
    ExitedEventArguments::getExitCode
)

val STEP_IN_TARGET_CODEC: PacketCodec<ByteBuf, StepInTarget> = PacketCodec.tuple(
    PacketCodecs.VAR_INT,
    StepInTarget::getId,
    PacketCodecs.STRING,
    StepInTarget::getLabel,
    NULLABLE_VAR_INT_PACKET_CODEC,
    StepInTarget::getLine,
    NULLABLE_VAR_INT_PACKET_CODEC,
    StepInTarget::getColumn,
    NULLABLE_VAR_INT_PACKET_CODEC,
    StepInTarget::getEndLine,
    NULLABLE_VAR_INT_PACKET_CODEC,
    StepInTarget::getEndColumn,
) { id, label, line, column, endLine, endColumn ->
    StepInTarget().apply {
        this.id = id
        this.label = label
        this.line = line
        this.column = column
        this.endLine = endLine
        this.endColumn = endColumn
    }
}

val STEP_IN_TARGETS_RESPONSE_PACKET_CODEC: PacketCodec<ByteBuf, StepInTargetsResponse> = STEP_IN_TARGET_CODEC.array().xmap(
    { StepInTargetsResponse().apply { targets = it } },
    { it.targets }
)

val VALUE_FORMAT_PACKET_CODEC: PacketCodec<ByteBuf, ValueFormat> = PacketCodecs.BOOLEAN.xmap(
    { ValueFormat().apply { hex = it }},
    ValueFormat::getHex
)
val NULLABLE_VALUE_FORMAT_PACKET_CODEC = VALUE_FORMAT_PACKET_CODEC.nullable()

val VARIABLES_ARGUMENTS_PACKET_CODEC: PacketCodec<ByteBuf, VariablesArguments> = PacketCodec.tuple(
    PacketCodecs.VAR_INT,
    VariablesArguments::getVariablesReference,
    enumConstantCodec(VariablesArgumentsFilter::class.java).nullable(),
    VariablesArguments::getFilter,
    NULLABLE_VAR_INT_PACKET_CODEC,
    VariablesArguments::getStart,
    NULLABLE_VAR_INT_PACKET_CODEC,
    VariablesArguments::getCount,
    NULLABLE_VALUE_FORMAT_PACKET_CODEC,
    VariablesArguments::getFormat
) { variablesReference, filter, start, count, format ->
    VariablesArguments().apply {
        this.variablesReference = variablesReference
        this.filter = filter
        this.start = start
        this.count = count
        this.format = format
    }
}

val VARIABLE_PRESENTATION_HINT_PACKET_CODEC: PacketCodec<ByteBuf, VariablePresentationHint> = PacketCodec.tuple(
    NULLABLE_STRING_PACKET_CODEC,
    VariablePresentationHint::getKind,
    PacketCodecs.STRING.array().nullable(),
    VariablePresentationHint::getAttributes,
    NULLABLE_STRING_PACKET_CODEC,
    VariablePresentationHint::getVisibility,
    NULLABLE_BOOL_PACKET_CODEC,
    VariablePresentationHint::getLazy
) { kind, attributes, visibility, lazy ->
    VariablePresentationHint().apply {
        this.kind = kind
        this.attributes = attributes
        this.visibility = visibility
        this.lazy = lazy
    }
}

val VARIABLE_PACKET_CODEC = object : PacketCodec<ByteBuf, Variable> {
    val NULLABLE_VARIABLE_PRESENTATION_HINT_CODEC = VARIABLE_PRESENTATION_HINT_PACKET_CODEC.nullable()
    override fun decode(buf: ByteBuf) = Variable().apply {
        name = PacketCodecs.STRING.decode(buf)
        value = PacketCodecs.STRING.decode(buf)
        type = NULLABLE_STRING_PACKET_CODEC.decode(buf)
        presentationHint = NULLABLE_VARIABLE_PRESENTATION_HINT_CODEC.decode(buf)
        evaluateName = NULLABLE_STRING_PACKET_CODEC.decode(buf)
        variablesReference = PacketCodecs.VAR_INT.decode(buf)
        namedVariables = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
        indexedVariables = NULLABLE_VAR_INT_PACKET_CODEC.decode(buf)
        memoryReference = NULLABLE_STRING_PACKET_CODEC.decode(buf)
    }

    override fun encode(buf: ByteBuf, value: Variable) {
        PacketCodecs.STRING.encode(buf, value.name)
        PacketCodecs.STRING.encode(buf, value.value)
        NULLABLE_STRING_PACKET_CODEC.encode(buf, value.type)
        NULLABLE_VARIABLE_PRESENTATION_HINT_CODEC.encode(buf, value.presentationHint)
        NULLABLE_STRING_PACKET_CODEC.encode(buf, value.evaluateName)
        PacketCodecs.VAR_INT.encode(buf, value.variablesReference)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.namedVariables)
        NULLABLE_VAR_INT_PACKET_CODEC.encode(buf, value.indexedVariables)
        NULLABLE_STRING_PACKET_CODEC.encode(buf, value.memoryReference)
    }
}

val SET_VARIABLE_ARGUMENTS_PACKET_CODEC: PacketCodec<ByteBuf, SetVariableArguments> = PacketCodec.tuple(
    PacketCodecs.VAR_INT,
    SetVariableArguments::getVariablesReference,
    PacketCodecs.STRING,
    SetVariableArguments::getName,
    PacketCodecs.STRING,
    SetVariableArguments::getValue,
    NULLABLE_VALUE_FORMAT_PACKET_CODEC,
    SetVariableArguments::getFormat
) { variablesReference, name, value, format ->
    SetVariableArguments().apply {
        this.variablesReference = variablesReference
        this.name = name
        this.value = value
        this.format = format
    }
}

val SET_VARIABLE_RESPONSE_PACKET_CODEC: PacketCodec<ByteBuf, SetVariableResponse> = PacketCodec.tuple(
    PacketCodecs.STRING,
    SetVariableResponse::getValue,
    NULLABLE_STRING_PACKET_CODEC,
    SetVariableResponse::getType,
    NULLABLE_VAR_INT_PACKET_CODEC,
    SetVariableResponse::getVariablesReference,
    NULLABLE_VAR_INT_PACKET_CODEC,
    SetVariableResponse::getNamedVariables,
    NULLABLE_VAR_INT_PACKET_CODEC,
    SetVariableResponse::getIndexedVariables
) { value, type, variablesReference, namedVariables, indexedVariables ->
    SetVariableResponse().apply {
        this.value = value
        this.type = type
        this.variablesReference = variablesReference
        this.namedVariables = namedVariables
        this.indexedVariables = indexedVariables
    }
}

val COMPLETION_ITEM_LABEL_DETAILS_PACKET_CODEC = PacketCodec.tuple(
    NULLABLE_STRING_PACKET_CODEC,
    CompletionItemLabelDetails::getDetail,
    NULLABLE_STRING_PACKET_CODEC,
    CompletionItemLabelDetails::getDescription,
) { detail, description ->
    CompletionItemLabelDetails().apply {
        this.detail = detail
        this.description = description
    }
}

val COMMAND_PACKET_CODEC: PacketCodec<ByteBuf, Command> = PacketCodec.tuple(
    PacketCodecs.STRING,
    Command::getTitle,
    PacketCodecs.STRING,
    Command::getCommand,
    OBJECT_PACKET_CODEC.list().nullable(),
    Command::getArguments,
    ::Command
)

val MARKUP_CONTENT_PACKET_CODEC: PacketCodec<ByteBuf, MarkupContent> = PacketCodec.tuple(
    PacketCodecs.STRING,
    MarkupContent::getKind,
    PacketCodecs.STRING,
    MarkupContent::getValue,
    ::MarkupContent
)

val TEXT_EDIT_PACKET_CODEC: PacketCodec<ByteBuf, TextEdit> = PacketCodec.tuple(
    RANGE_PACKET_CODEC,
    TextEdit::getRange,
    PacketCodecs.STRING,
    TextEdit::getNewText,
    ::TextEdit
)
val INSERT_REPLACE_EDIT_PACKET_CODEC: PacketCodec<ByteBuf, InsertReplaceEdit> = PacketCodec.tuple(
    PacketCodecs.STRING,
    InsertReplaceEdit::getNewText,
    RANGE_PACKET_CODEC,
    InsertReplaceEdit::getInsert,
    RANGE_PACKET_CODEC,
    InsertReplaceEdit::getReplace,
) { newText, insert, replace ->
    InsertReplaceEdit().apply {
        this.newText = newText
        this.insert = insert
        this.replace = replace
    }
}

@Suppress("DEPRECATION")
val COMPLETION_ITEM_PACKET_CODEC = object : PacketCodec<ByteBuf, CompletionItem> {
    val NULLABLE_COMPLETION_ITEM_LABEL_DETAILS_CODEC = COMPLETION_ITEM_LABEL_DETAILS_PACKET_CODEC.nullable()
    val NULLABLE_COMPLETION_ITEM_KIND_CODEC = enumConstantCodec(CompletionItemKind::class.java).nullable()
    val NULLABLE_COMPLETION_ITEM_TAGS_CODEC = enumConstantCodec(CompletionItemTag::class.java).list().nullable()
    val NULLABLE_COMPLETION_ITEM_INSERT_TEXT_FORMAT_CODEC = enumConstantCodec(InsertTextFormat::class.java).nullable()
    val NULLABLE_COMPLETION_ITEM_INSERT_TEXT_MODE_CODEC = enumConstantCodec(InsertTextMode::class.java).nullable()
    val NULLABLE_COMPLETION_ITEM_COMMIT_CHARACTERS_CODEC = PacketCodecs.STRING.list().nullable()
    val NULLABLE_DOCUMENTATION_CODEC = (PacketCodecs.STRING makeEither MARKUP_CONTENT_PACKET_CODEC).nullable()
    val NULLABLE_TEXT_EDIT_EITHER_CODEC = (TEXT_EDIT_PACKET_CODEC makeEither INSERT_REPLACE_EDIT_PACKET_CODEC).nullable()
    val NULLABLE_TEXT_EDITS_CODEC = TEXT_EDIT_PACKET_CODEC.list().nullable()

    override fun decode(buf: ByteBuf) = CompletionItem().apply {
        label = PacketCodecs.STRING.decode(buf)
        labelDetails = NULLABLE_COMPLETION_ITEM_LABEL_DETAILS_CODEC.decode(buf)
        kind = NULLABLE_COMPLETION_ITEM_KIND_CODEC.decode(buf)
        tags = NULLABLE_COMPLETION_ITEM_TAGS_CODEC.decode(buf)
        detail = NULLABLE_STRING_PACKET_CODEC.decode(buf)
        documentation = NULLABLE_DOCUMENTATION_CODEC.decode(buf)
        deprecated = NULLABLE_BOOL_PACKET_CODEC.decode(buf)
        preselect = NULLABLE_BOOL_PACKET_CODEC.decode(buf)
        sortText = NULLABLE_STRING_PACKET_CODEC.decode(buf)
        filterText = NULLABLE_STRING_PACKET_CODEC.decode(buf)
        insertText = NULLABLE_STRING_PACKET_CODEC.decode(buf)
        insertTextFormat = NULLABLE_COMPLETION_ITEM_INSERT_TEXT_FORMAT_CODEC.decode(buf)
        insertTextMode = NULLABLE_COMPLETION_ITEM_INSERT_TEXT_MODE_CODEC.decode(buf)
        textEdit = NULLABLE_TEXT_EDIT_EITHER_CODEC.decode(buf)
        additionalTextEdits = NULLABLE_TEXT_EDITS_CODEC.decode(buf)
        commitCharacters = NULLABLE_COMPLETION_ITEM_COMMIT_CHARACTERS_CODEC.decode(buf)
        data = NULLABLE_OBJECT_PACKET_CODEC.decode(buf)
    }

    override fun encode(buf: ByteBuf, value: CompletionItem) {
        PacketCodecs.STRING.encode(buf, value.label)
        NULLABLE_COMPLETION_ITEM_LABEL_DETAILS_CODEC.encode(buf, value.labelDetails)
        NULLABLE_COMPLETION_ITEM_KIND_CODEC.encode(buf, value.kind)
        NULLABLE_COMPLETION_ITEM_TAGS_CODEC.encode(buf, value.tags)
        NULLABLE_STRING_PACKET_CODEC.encode(buf, value.detail)
        NULLABLE_DOCUMENTATION_CODEC.encode(buf, value.documentation)
        NULLABLE_BOOL_PACKET_CODEC.encode(buf, value.deprecated)
        NULLABLE_BOOL_PACKET_CODEC.encode(buf, value.preselect)
        NULLABLE_STRING_PACKET_CODEC.encode(buf, value.sortText)
        NULLABLE_STRING_PACKET_CODEC.encode(buf, value.filterText)
        NULLABLE_STRING_PACKET_CODEC.encode(buf, value.insertText)
        NULLABLE_COMPLETION_ITEM_INSERT_TEXT_FORMAT_CODEC.encode(buf, value.insertTextFormat)
        NULLABLE_COMPLETION_ITEM_INSERT_TEXT_MODE_CODEC.encode(buf, value.insertTextMode)
        NULLABLE_TEXT_EDIT_EITHER_CODEC.encode(buf, value.textEdit)
        NULLABLE_TEXT_EDITS_CODEC.encode(buf, value.additionalTextEdits)
        NULLABLE_COMPLETION_ITEM_COMMIT_CHARACTERS_CODEC.encode(buf, value.commitCharacters)
        NULLABLE_OBJECT_PACKET_CODEC.encode(buf, value.data)
    }
}
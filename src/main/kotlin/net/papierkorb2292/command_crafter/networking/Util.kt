package net.papierkorb2292.command_crafter.networking

import com.google.gson.Gson
import com.google.gson.JsonElement
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.*
import java.util.function.Function

fun <B : ByteBuf, V: Any> StreamCodec<B, V>.optional(): StreamCodec<B, Optional<V>> = ByteBufCodecs.optional(this)
fun <T, R: Any> ((T) -> R?).toOptional(): Function<T, Optional<R>> = Function { Optional.ofNullable(this.invoke(it)) }
fun <B : ByteBuf, V: Any> StreamCodec<B, V>.list(): StreamCodec<B, List<V>> =
    ByteBufCodecs.collection(::ArrayList, this)
inline fun <B : ByteBuf, reified V: Any> StreamCodec<B, V>.array(): StreamCodec<B, Array<V>> = list().map(
    List<V>::toTypedArray,
    Array<V>::toList
)
infix fun <B : ByteBuf, V1: Any, V2: Any> StreamCodec<B, V1>.makeEither(other: StreamCodec<B, V2>) = object : StreamCodec<B, Either<V1, V2>> {
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

fun <T: Enum<T>> enumConstantCodec(enumClass: Class<T>): StreamCodec<ByteBuf, T> {
    val values = enumClass.enumConstants
    return ByteBufCodecs.VAR_INT.map(values::get) { it.ordinal }
}

val UNIT_CODEC = StreamCodec.unit<ByteBuf, Unit>(Unit)

val OPTIONAL_STRING_PACKET_CODEC = ByteBufCodecs.STRING_UTF8.optional()
val OPTIONAL_BOOL_PACKET_CODEC = ByteBufCodecs.BOOL.optional()
val OPTIONAL_VAR_INT_PACKET_CODEC = ByteBufCodecs.VAR_INT.optional()

val POSITION_PACKET_CODEC: StreamCodec<ByteBuf, Position> = StreamCodec.composite(
    ByteBufCodecs.VAR_INT,
    Position::getLine,
    ByteBufCodecs.VAR_INT,
    Position::getCharacter,
    ::Position
)

val RANGE_PACKET_CODEC: StreamCodec<ByteBuf, Range> = StreamCodec.composite(
    POSITION_PACKET_CODEC,
    Range::getStart,
    POSITION_PACKET_CODEC,
    Range::getEnd,
    ::Range
)

val OBJECT_PACKET_CODEC_GSON = Gson()
val OBJECT_PACKET_CODEC: StreamCodec<ByteBuf, Any> = ByteBufCodecs.STRING_UTF8.map<Any>(
    { OBJECT_PACKET_CODEC_GSON.fromJson(it, JsonElement::class.java) },
    { OBJECT_PACKET_CODEC_GSON.toJson(it) }
)
val OPTIONAL_OBJECT_PACKET_CODEC = OBJECT_PACKET_CODEC.optional()

val SOURCE_PACKET_CODEC: StreamCodec<ByteBuf, Source> = StreamCodec.recursive { self ->
    object : StreamCodec<ByteBuf, Source> {
        val CHILDREN_CODEC = self.array().optional()
        val PRESENTATION_HINT_CODEC = enumConstantCodec(SourcePresentationHint::class.java).optional()

        override fun decode(buf: ByteBuf) = Source().apply {
            name = ByteBufCodecs.STRING_UTF8.decode(buf)
            path = OPTIONAL_STRING_PACKET_CODEC.decode(buf).orElse(null)
            sourceReference = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
            presentationHint = PRESENTATION_HINT_CODEC.decode(buf).orElse(null)
            origin = OPTIONAL_STRING_PACKET_CODEC.decode(buf).orElse(null)
            sources = CHILDREN_CODEC.decode(buf).orElse(null)
        }

        override fun encode(buf: ByteBuf, value: Source) {
            ByteBufCodecs.STRING_UTF8.encode(buf, value.name)
            OPTIONAL_STRING_PACKET_CODEC.encode(buf, Optional.ofNullable(value.path))
            OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.sourceReference))
            PRESENTATION_HINT_CODEC.encode(buf, Optional.ofNullable(value.presentationHint))
            OPTIONAL_STRING_PACKET_CODEC.encode(buf, Optional.ofNullable(value.origin))
            CHILDREN_CODEC.encode(buf, Optional.ofNullable(value.sources))
        }
    }
}
val OPTIONAL_SOURCE_CODEC = SOURCE_PACKET_CODEC.optional()

val BREAKPOINT_PACKET_CODEC = object : StreamCodec<ByteBuf, Breakpoint> {
    override fun decode(buf: ByteBuf) = Breakpoint().apply {
        id = ByteBufCodecs.VAR_INT.decode(buf)
        isVerified = ByteBufCodecs.BOOL.decode(buf)
        message = OPTIONAL_STRING_PACKET_CODEC.decode(buf).orElse(null)
        source = OPTIONAL_SOURCE_CODEC.decode(buf).orElse(null)
        line = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
        column = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
        endLine = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
        endColumn = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
    }

    override fun encode(buf: ByteBuf, value: Breakpoint) {
        ByteBufCodecs.VAR_INT.encode(buf, value.id)
        ByteBufCodecs.BOOL.encode(buf, value.isVerified)
        OPTIONAL_STRING_PACKET_CODEC.encode(buf, Optional.ofNullable(value.message))
        OPTIONAL_SOURCE_CODEC.encode(buf, Optional.ofNullable(value.source))
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.line))
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.column))
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.endLine))
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.endColumn))
    }
}

val SOURCE_BREAKPOINT_PACKET_CODEC: StreamCodec<ByteBuf, SourceBreakpoint> = StreamCodec.composite(
    ByteBufCodecs.VAR_INT,
    SourceBreakpoint::getLine,
    OPTIONAL_VAR_INT_PACKET_CODEC,
    SourceBreakpoint::getColumn.toOptional(),
    OPTIONAL_STRING_PACKET_CODEC,
    SourceBreakpoint::getCondition.toOptional(),
    OPTIONAL_STRING_PACKET_CODEC,
    SourceBreakpoint::getHitCondition.toOptional(),
    OPTIONAL_STRING_PACKET_CODEC,
    SourceBreakpoint::getLogMessage.toOptional()
) { line, column, condition, hitCondition, logMessage ->
    SourceBreakpoint().apply {
        this.line = line
        this.column = column.orElse(null)
        this.condition = condition.orElse(null)
        this.hitCondition = hitCondition.orElse(null)
        this.logMessage = logMessage.orElse(null)
    }
}

val UNPARSED_BREAKPOINT_PACKET_CODEC: StreamCodec<ByteBuf, UnparsedServerBreakpoint> = StreamCodec.composite(
    ByteBufCodecs.VAR_INT,
    UnparsedServerBreakpoint::id,
    OPTIONAL_VAR_INT_PACKET_CODEC,
    UnparsedServerBreakpoint::sourceReference.toOptional(),
    SOURCE_BREAKPOINT_PACKET_CODEC,
    UnparsedServerBreakpoint::sourceBreakpoint
) { id, sourceReference, sourceBreakpoint ->
    UnparsedServerBreakpoint(id, sourceReference.orElse(null), sourceBreakpoint)
}

val SOURCE_RESPONSE_PACKET_CODEC: StreamCodec<ByteBuf, SourceResponse> = StreamCodec.composite(
    ByteBufCodecs.STRING_UTF8,
    SourceResponse::getContent,
    OPTIONAL_STRING_PACKET_CODEC,
    SourceResponse::getMimeType.toOptional()
) { content, mimeType ->
    SourceResponse().apply {
        this.content = content
        this.mimeType = mimeType.orElse(null)
    }
}

val SCOPE_PACKET_CODEC = object : StreamCodec<ByteBuf, Scope> {
    override fun decode(buf: ByteBuf) = Scope().apply {
        name = ByteBufCodecs.STRING_UTF8.decode(buf)
        presentationHint = OPTIONAL_STRING_PACKET_CODEC.decode(buf).orElse(null)
        variablesReference = ByteBufCodecs.VAR_INT.decode(buf)
        namedVariables = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
        indexedVariables = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
        isExpensive = ByteBufCodecs.BOOL.decode(buf)
        source = OPTIONAL_SOURCE_CODEC.decode(buf).orElse(null)
        line = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
        column = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
        endLine = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
        endColumn = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
    }

    override fun encode(buf: ByteBuf, value: Scope) {
        ByteBufCodecs.STRING_UTF8.encode(buf, value.name)
        OPTIONAL_STRING_PACKET_CODEC.encode(buf, Optional.ofNullable(value.presentationHint))
        ByteBufCodecs.VAR_INT.encode(buf, value.variablesReference)
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.namedVariables))
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.indexedVariables))
        ByteBufCodecs.BOOL.encode(buf, value.isExpensive)
        OPTIONAL_SOURCE_CODEC.encode(buf, Optional.ofNullable(value.source))
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.line))
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.column))
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.endLine))
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.endColumn))
    }
}

val STOPPED_EVENT_ARGUMENTS_PACKET_CODEC = object : StreamCodec<ByteBuf, StoppedEventArguments> {
    val HIT_BREAKPOINT_IDS_CODEC = ByteBufCodecs.VAR_INT.array().optional()
    override fun decode(buf: ByteBuf) = StoppedEventArguments().apply {
        reason = ByteBufCodecs.STRING_UTF8.decode(buf)
        description = OPTIONAL_STRING_PACKET_CODEC.decode(buf).orElse(null)
        allThreadsStopped = OPTIONAL_BOOL_PACKET_CODEC.decode(buf).orElse(null)
        text = OPTIONAL_STRING_PACKET_CODEC.decode(buf).orElse(null)
        threadId = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
        hitBreakpointIds = HIT_BREAKPOINT_IDS_CODEC.decode(buf).orElse(null)
        preserveFocusHint = OPTIONAL_BOOL_PACKET_CODEC.decode(buf).orElse(null)
    }

    override fun encode(buf: ByteBuf, value: StoppedEventArguments) {
        ByteBufCodecs.STRING_UTF8.encode(buf, value.reason)
        OPTIONAL_STRING_PACKET_CODEC.encode(buf, Optional.ofNullable(value.description))
        OPTIONAL_BOOL_PACKET_CODEC.encode(buf, Optional.ofNullable(value.allThreadsStopped))
        OPTIONAL_STRING_PACKET_CODEC.encode(buf, Optional.ofNullable(value.text))
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.threadId))
        HIT_BREAKPOINT_IDS_CODEC.encode(buf, Optional.ofNullable(value.hitBreakpointIds))
        OPTIONAL_BOOL_PACKET_CODEC.encode(buf, Optional.ofNullable(value.preserveFocusHint))
    }
}

val BREAKPOINT_EVENT_ARGUMENTS_PACKET_CODEC: StreamCodec<ByteBuf, BreakpointEventArguments> = StreamCodec.composite(
    ByteBufCodecs.STRING_UTF8,
    BreakpointEventArguments::getReason,
    BREAKPOINT_PACKET_CODEC,
    BreakpointEventArguments::getBreakpoint
) { breakpointId, breakpointData ->
    BreakpointEventArguments().apply {
        reason = breakpointId
        breakpoint = breakpointData
    }
}

val OUTPUT_EVENT_ARGUMENTS_PACKET_CODEC = object : StreamCodec<ByteBuf, OutputEventArguments> {
    val GROUP_CODEC = enumConstantCodec(OutputEventArgumentsGroup::class.java).optional()

    override fun decode(buf: ByteBuf) = OutputEventArguments().apply {
        category = OPTIONAL_STRING_PACKET_CODEC.decode(buf).orElse(null)
        output = ByteBufCodecs.STRING_UTF8.decode(buf)
        data = OPTIONAL_OBJECT_PACKET_CODEC.decode(buf)
        source = OPTIONAL_SOURCE_CODEC.decode(buf).orElse(null)
        line = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
        column = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
        variablesReference = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
        group = GROUP_CODEC.decode(buf).orElse(null)
    }

    override fun encode(buf: ByteBuf, value: OutputEventArguments) {
        OPTIONAL_STRING_PACKET_CODEC.encode(buf, Optional.ofNullable(value.category))
        ByteBufCodecs.STRING_UTF8.encode(buf, value.output)
        OPTIONAL_OBJECT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.data))
        OPTIONAL_SOURCE_CODEC.encode(buf, Optional.ofNullable(value.source))
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.line))
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.column))
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.variablesReference))
        GROUP_CODEC.encode(buf, Optional.ofNullable(value.group))
    }
}

val EXITED_EVENT_ARGUMENTS_PACKET_CODEC: StreamCodec<ByteBuf, ExitedEventArguments> = ByteBufCodecs.VAR_INT.map(
    { ExitedEventArguments().apply { exitCode = it }},
    ExitedEventArguments::getExitCode
)

val STEP_IN_TARGET_CODEC: StreamCodec<ByteBuf, StepInTarget> = StreamCodec.composite(
    ByteBufCodecs.VAR_INT,
    StepInTarget::getId,
    ByteBufCodecs.STRING_UTF8,
    StepInTarget::getLabel,
    OPTIONAL_VAR_INT_PACKET_CODEC,
    StepInTarget::getLine.toOptional(),
    OPTIONAL_VAR_INT_PACKET_CODEC,
    StepInTarget::getColumn.toOptional(),
    OPTIONAL_VAR_INT_PACKET_CODEC,
    StepInTarget::getEndLine.toOptional(),
    OPTIONAL_VAR_INT_PACKET_CODEC,
    StepInTarget::getEndColumn.toOptional(),
) { id, label, line, column, endLine, endColumn ->
    StepInTarget().apply {
        this.id = id
        this.label = label
        this.line = line.orElse(null)
        this.column = column.orElse(null)
        this.endLine = endLine.orElse(null)
        this.endColumn = endColumn.orElse(null)
    }
}

val STEP_IN_TARGETS_RESPONSE_PACKET_CODEC: StreamCodec<ByteBuf, StepInTargetsResponse> = STEP_IN_TARGET_CODEC.array().map(
    { StepInTargetsResponse().apply { targets = it } },
    { it.targets }
)

val VALUE_FORMAT_PACKET_CODEC: StreamCodec<ByteBuf, ValueFormat> = ByteBufCodecs.BOOL.map(
    { ValueFormat().apply { hex = it }},
    ValueFormat::getHex
)
val NULLABLE_VALUE_FORMAT_PACKET_CODEC = VALUE_FORMAT_PACKET_CODEC.optional()

val VARIABLES_ARGUMENTS_PACKET_CODEC: StreamCodec<ByteBuf, VariablesArguments> = StreamCodec.composite(
    ByteBufCodecs.VAR_INT,
    VariablesArguments::getVariablesReference,
    enumConstantCodec(VariablesArgumentsFilter::class.java).optional(),
    VariablesArguments::getFilter.toOptional(),
    OPTIONAL_VAR_INT_PACKET_CODEC,
    VariablesArguments::getStart.toOptional(),
    OPTIONAL_VAR_INT_PACKET_CODEC,
    VariablesArguments::getCount.toOptional(),
    NULLABLE_VALUE_FORMAT_PACKET_CODEC,
    VariablesArguments::getFormat.toOptional()
) { variablesReference, filter, start, count, format ->
    VariablesArguments().apply {
        this.variablesReference = variablesReference
        this.filter = filter.orElse(null)
        this.start = start.orElse(null)
        this.count = count.orElse(null)
        this.format = format.orElse(null)
    }
}

val VARIABLE_PRESENTATION_HINT_PACKET_CODEC: StreamCodec<ByteBuf, VariablePresentationHint> = StreamCodec.composite(
    OPTIONAL_STRING_PACKET_CODEC,
    VariablePresentationHint::getKind.toOptional(),
    ByteBufCodecs.STRING_UTF8.array().optional(),
    VariablePresentationHint::getAttributes.toOptional(),
    OPTIONAL_STRING_PACKET_CODEC,
    VariablePresentationHint::getVisibility.toOptional(),
    OPTIONAL_BOOL_PACKET_CODEC,
    VariablePresentationHint::getLazy.toOptional()
) { kind, attributes, visibility, lazy ->
    VariablePresentationHint().apply {
        this.kind = kind.orElse(null)
        this.attributes = attributes.orElse(null)
        this.visibility = visibility.orElse(null)
        this.lazy = lazy.orElse(null)
    }
}

val VARIABLE_PACKET_CODEC = object : StreamCodec<ByteBuf, Variable> {
    val NULLABLE_VARIABLE_PRESENTATION_HINT_CODEC = VARIABLE_PRESENTATION_HINT_PACKET_CODEC.optional()
    override fun decode(buf: ByteBuf) = Variable().apply {
        name = ByteBufCodecs.STRING_UTF8.decode(buf)
        value = ByteBufCodecs.STRING_UTF8.decode(buf)
        type = OPTIONAL_STRING_PACKET_CODEC.decode(buf).orElse(null)
        presentationHint = NULLABLE_VARIABLE_PRESENTATION_HINT_CODEC.decode(buf).orElse(null)
        evaluateName = OPTIONAL_STRING_PACKET_CODEC.decode(buf).orElse(null)
        variablesReference = ByteBufCodecs.VAR_INT.decode(buf)
        namedVariables = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
        indexedVariables = OPTIONAL_VAR_INT_PACKET_CODEC.decode(buf).orElse(null)
        memoryReference = OPTIONAL_STRING_PACKET_CODEC.decode(buf).orElse(null)
    }

    override fun encode(buf: ByteBuf, value: Variable) {
        ByteBufCodecs.STRING_UTF8.encode(buf, value.name)
        ByteBufCodecs.STRING_UTF8.encode(buf, value.value)
        OPTIONAL_STRING_PACKET_CODEC.encode(buf, Optional.ofNullable(value.type))
        NULLABLE_VARIABLE_PRESENTATION_HINT_CODEC.encode(buf, Optional.ofNullable(value.presentationHint))
        OPTIONAL_STRING_PACKET_CODEC.encode(buf, Optional.ofNullable(value.evaluateName))
        ByteBufCodecs.VAR_INT.encode(buf, value.variablesReference)
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.namedVariables))
        OPTIONAL_VAR_INT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.indexedVariables))
        OPTIONAL_STRING_PACKET_CODEC.encode(buf, Optional.ofNullable(value.memoryReference))
    }
}

val SET_VARIABLE_ARGUMENTS_PACKET_CODEC: StreamCodec<ByteBuf, SetVariableArguments> = StreamCodec.composite(
    ByteBufCodecs.VAR_INT,
    SetVariableArguments::getVariablesReference,
    ByteBufCodecs.STRING_UTF8,
    SetVariableArguments::getName,
    ByteBufCodecs.STRING_UTF8,
    SetVariableArguments::getValue,
    NULLABLE_VALUE_FORMAT_PACKET_CODEC,
    SetVariableArguments::getFormat.toOptional()
) { variablesReference, name, value, format ->
    SetVariableArguments().apply {
        this.variablesReference = variablesReference
        this.name = name
        this.value = value
        this.format = format.orElse(null)
    }
}

val SET_VARIABLE_RESPONSE_PACKET_CODEC: StreamCodec<ByteBuf, SetVariableResponse> = StreamCodec.composite(
    ByteBufCodecs.STRING_UTF8,
    SetVariableResponse::getValue,
    OPTIONAL_STRING_PACKET_CODEC,
    SetVariableResponse::getType.toOptional(),
    OPTIONAL_VAR_INT_PACKET_CODEC,
    SetVariableResponse::getVariablesReference.toOptional(),
    OPTIONAL_VAR_INT_PACKET_CODEC,
    SetVariableResponse::getNamedVariables.toOptional(),
    OPTIONAL_VAR_INT_PACKET_CODEC,
    SetVariableResponse::getIndexedVariables.toOptional()
) { value, type, variablesReference, namedVariables, indexedVariables ->
    SetVariableResponse().apply {
        this.value = value
        this.type = type.orElse(null)
        this.variablesReference = variablesReference.orElse(null)
        this.namedVariables = namedVariables.orElse(null)
        this.indexedVariables = indexedVariables.orElse(null)
    }
}

val COMPLETION_ITEM_LABEL_DETAILS_PACKET_CODEC = StreamCodec.composite(
    OPTIONAL_STRING_PACKET_CODEC,
    CompletionItemLabelDetails::getDetail.toOptional(),
    OPTIONAL_STRING_PACKET_CODEC,
    CompletionItemLabelDetails::getDescription.toOptional(),
) { detail, description ->
    CompletionItemLabelDetails().apply {
        this.detail = detail.orElse(null)
        this.description = description.orElse(null)
    }
}

val COMMAND_PACKET_CODEC: StreamCodec<ByteBuf, Command> = StreamCodec.composite(
    ByteBufCodecs.STRING_UTF8,
    Command::getTitle,
    ByteBufCodecs.STRING_UTF8,
    Command::getCommand,
    OBJECT_PACKET_CODEC.list().optional(),
    Command::getArguments.toOptional(),
) { title, command, arguments ->
    Command().apply {
        this.title = title
        this.command = command
        this.arguments = arguments.orElse(null)
    }
}

val MARKUP_CONTENT_PACKET_CODEC: StreamCodec<ByteBuf, MarkupContent> = StreamCodec.composite(
    ByteBufCodecs.STRING_UTF8,
    MarkupContent::getKind,
    ByteBufCodecs.STRING_UTF8,
    MarkupContent::getValue,
    ::MarkupContent
)

val TEXT_EDIT_PACKET_CODEC: StreamCodec<ByteBuf, TextEdit> = StreamCodec.composite(
    RANGE_PACKET_CODEC,
    TextEdit::getRange,
    ByteBufCodecs.STRING_UTF8,
    TextEdit::getNewText,
    ::TextEdit
)
val INSERT_REPLACE_EDIT_PACKET_CODEC: StreamCodec<ByteBuf, InsertReplaceEdit> = StreamCodec.composite(
    ByteBufCodecs.STRING_UTF8,
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
val COMPLETION_ITEM_PACKET_CODEC = object : StreamCodec<ByteBuf, CompletionItem> {
    val NULLABLE_COMPLETION_ITEM_LABEL_DETAILS_CODEC = COMPLETION_ITEM_LABEL_DETAILS_PACKET_CODEC.optional()
    val NULLABLE_COMPLETION_ITEM_KIND_CODEC = enumConstantCodec(CompletionItemKind::class.java).optional()
    val NULLABLE_COMPLETION_ITEM_TAGS_CODEC = enumConstantCodec(CompletionItemTag::class.java).list().optional()
    val NULLABLE_COMPLETION_ITEM_INSERT_TEXT_FORMAT_CODEC = enumConstantCodec(InsertTextFormat::class.java).optional()
    val NULLABLE_COMPLETION_ITEM_INSERT_TEXT_MODE_CODEC = enumConstantCodec(InsertTextMode::class.java).optional()
    val NULLABLE_COMPLETION_ITEM_COMMIT_CHARACTERS_CODEC = ByteBufCodecs.STRING_UTF8.list().optional()
    val NULLABLE_DOCUMENTATION_CODEC = (ByteBufCodecs.STRING_UTF8 makeEither MARKUP_CONTENT_PACKET_CODEC).optional()
    val NULLABLE_TEXT_EDIT_EITHER_CODEC = (TEXT_EDIT_PACKET_CODEC makeEither INSERT_REPLACE_EDIT_PACKET_CODEC).optional()
    val NULLABLE_TEXT_EDITS_CODEC = TEXT_EDIT_PACKET_CODEC.list().optional()

    override fun decode(buf: ByteBuf) = CompletionItem().apply {
        label = ByteBufCodecs.STRING_UTF8.decode(buf)
        labelDetails = NULLABLE_COMPLETION_ITEM_LABEL_DETAILS_CODEC.decode(buf).orElse(null)
        kind = NULLABLE_COMPLETION_ITEM_KIND_CODEC.decode(buf).orElse(null)
        tags = NULLABLE_COMPLETION_ITEM_TAGS_CODEC.decode(buf).orElse(null)
        detail = OPTIONAL_STRING_PACKET_CODEC.decode(buf).orElse(null)
        documentation = NULLABLE_DOCUMENTATION_CODEC.decode(buf).orElse(null)
        deprecated = OPTIONAL_BOOL_PACKET_CODEC.decode(buf).orElse(null)
        preselect = OPTIONAL_BOOL_PACKET_CODEC.decode(buf).orElse(null)
        sortText = OPTIONAL_STRING_PACKET_CODEC.decode(buf).orElse(null)
        filterText = OPTIONAL_STRING_PACKET_CODEC.decode(buf).orElse(null)
        insertText = OPTIONAL_STRING_PACKET_CODEC.decode(buf).orElse(null)
        insertTextFormat = NULLABLE_COMPLETION_ITEM_INSERT_TEXT_FORMAT_CODEC.decode(buf).orElse(null)
        insertTextMode = NULLABLE_COMPLETION_ITEM_INSERT_TEXT_MODE_CODEC.decode(buf).orElse(null)
        textEdit = NULLABLE_TEXT_EDIT_EITHER_CODEC.decode(buf).orElse(null)
        additionalTextEdits = NULLABLE_TEXT_EDITS_CODEC.decode(buf).orElse(null)
        commitCharacters = NULLABLE_COMPLETION_ITEM_COMMIT_CHARACTERS_CODEC.decode(buf).orElse(null)
        data = OPTIONAL_OBJECT_PACKET_CODEC.decode(buf)
    }

    override fun encode(buf: ByteBuf, value: CompletionItem) {
        ByteBufCodecs.STRING_UTF8.encode(buf, value.label)
        NULLABLE_COMPLETION_ITEM_LABEL_DETAILS_CODEC.encode(buf, Optional.ofNullable(value.labelDetails))
        NULLABLE_COMPLETION_ITEM_KIND_CODEC.encode(buf, Optional.ofNullable(value.kind))
        NULLABLE_COMPLETION_ITEM_TAGS_CODEC.encode(buf, Optional.ofNullable(value.tags))
        OPTIONAL_STRING_PACKET_CODEC.encode(buf, Optional.ofNullable(value.detail))
        NULLABLE_DOCUMENTATION_CODEC.encode(buf, Optional.ofNullable(value.documentation))
        OPTIONAL_BOOL_PACKET_CODEC.encode(buf, Optional.ofNullable(value.deprecated))
        OPTIONAL_BOOL_PACKET_CODEC.encode(buf, Optional.ofNullable(value.preselect))
        OPTIONAL_STRING_PACKET_CODEC.encode(buf, Optional.ofNullable(value.sortText))
        OPTIONAL_STRING_PACKET_CODEC.encode(buf, Optional.ofNullable(value.filterText))
        OPTIONAL_STRING_PACKET_CODEC.encode(buf, Optional.ofNullable(value.insertText))
        NULLABLE_COMPLETION_ITEM_INSERT_TEXT_FORMAT_CODEC.encode(buf, Optional.ofNullable(value.insertTextFormat))
        NULLABLE_COMPLETION_ITEM_INSERT_TEXT_MODE_CODEC.encode(buf, Optional.ofNullable(value.insertTextMode))
        NULLABLE_TEXT_EDIT_EITHER_CODEC.encode(buf, Optional.ofNullable(value.textEdit))
        NULLABLE_TEXT_EDITS_CODEC.encode(buf, Optional.ofNullable(value.additionalTextEdits))
        NULLABLE_COMPLETION_ITEM_COMMIT_CHARACTERS_CODEC.encode(buf, Optional.ofNullable(value.commitCharacters))
        OPTIONAL_OBJECT_PACKET_CODEC.encode(buf, Optional.ofNullable(value.data))
    }
}
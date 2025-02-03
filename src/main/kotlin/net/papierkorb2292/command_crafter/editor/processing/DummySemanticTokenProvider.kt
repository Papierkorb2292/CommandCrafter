package net.papierkorb2292.command_crafter.editor.processing

object DummySemanticTokenProvider : StringRangeTree.SemanticTokenProvider<Any?> {
    override fun getMapNameTokenInfo(map: Any?) = null
    override fun getNodeTokenInfo(node: Any?) = null
    override fun getAdditionalTokens(node: Any?) = emptyList<StringRangeTree.AdditionalToken>()
}
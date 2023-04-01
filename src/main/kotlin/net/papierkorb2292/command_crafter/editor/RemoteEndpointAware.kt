package net.papierkorb2292.command_crafter.editor

import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint

interface RemoteEndpointAware {
    fun setRemoteEndpoint(remote: RemoteEndpoint)
}
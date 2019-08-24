package com.touuki.netty.jsonrpc;

/**
 * The JSON-RPC Request mode, whether it's a request need a response or a
 * notification. The AUTO will determine by the return type.
 *
 */
public enum JsonRpcRequestMode {
	AUTO, REQUEST, NOTIFICATION
}

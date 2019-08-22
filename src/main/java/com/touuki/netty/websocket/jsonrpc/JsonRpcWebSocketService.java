package com.touuki.netty.websocket.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;

import io.netty.channel.Channel;

public interface JsonRpcWebSocketService {
	Object handleRequest(Channel channel, String method, JsonNode params);
	void channelInactive(Channel channel);
}

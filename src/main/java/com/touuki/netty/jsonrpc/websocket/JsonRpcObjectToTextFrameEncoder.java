package com.touuki.netty.jsonrpc.websocket;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.touuki.netty.jsonrpc.JsonRpcObject;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

@Sharable
public class JsonRpcObjectToTextFrameEncoder extends MessageToMessageEncoder<JsonRpcObject> {
	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	protected void encode(ChannelHandlerContext ctx, JsonRpcObject msg, List<Object> out) throws JsonProcessingException {
		out.add(new TextWebSocketFrame(objectMapper.writeValueAsString(msg)));
	}

}

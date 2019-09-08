package com.touuki.netty.jsonrpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.MessageToByteEncoder;

@Sharable
public class JsonRpcObjectEncoder extends MessageToByteEncoder<JsonRpcObject> {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	protected void encode(ChannelHandlerContext ctx, JsonRpcObject msg, ByteBuf out) throws JsonProcessingException {
		out.writeBytes(objectMapper.writeValueAsBytes(msg));
	}

}

package com.touuki.netty.jsonrpc;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.json.JsonObjectDecoder;

public class JsonRpcProtocolHandler extends ByteToMessageCodec<JsonRpcObject> {

	private final JsonRpcClientHandler jsonRpcClientHandler;
	private final JsonRpcServerHandler jsonRpcServerHandler;
	
	public JsonRpcProtocolHandler(JsonRpcClientHandler jsonRpcClientHandler) {
		this(jsonRpcClientHandler, null);
	}
	
	public JsonRpcProtocolHandler(JsonRpcServerHandler jsonRpcServerHandler) {
		this(null, jsonRpcServerHandler);
	}
	
	public JsonRpcProtocolHandler(JsonRpcClientHandler jsonRpcClientHandler, JsonRpcServerHandler jsonRpcServerHandler) {
		this.jsonRpcClientHandler = jsonRpcClientHandler;
		this.jsonRpcServerHandler = jsonRpcServerHandler;
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		ChannelPipeline cp = ctx.pipeline();
		if (cp.get(JsonObjectDecoder.class) == null) {
			cp.addBefore(ctx.name(), JsonObjectDecoder.class.getName(), new JsonObjectDecoder());
		}
		if (cp.get(JsonNodeToJsonRpcObjectDecoder.class) == null) {
			cp.addAfter(ctx.name(), JsonNodeToJsonRpcObjectDecoder.class.getName(),
					new JsonNodeToJsonRpcObjectDecoder(jsonRpcClientHandler, jsonRpcServerHandler));
		}
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws IOException {
		byte[] byteArray = new byte[in.readableBytes()];
		in.readBytes(byteArray);
		out.add(JsonUtils.MAPPER.readTree(byteArray));
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, JsonRpcObject msg, ByteBuf out) throws JsonProcessingException {
		out.writeBytes(JsonUtils.MAPPER.writeValueAsBytes(msg));
	}

}

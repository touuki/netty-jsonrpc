package com.touuki.netty.jsonrpc;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.json.JsonObjectDecoder;

public class JsonNodeDecoder extends ByteToMessageDecoder {
	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		ChannelPipeline cp = ctx.pipeline();
		if (cp.get(JsonObjectDecoder.class) == null) {
			ctx.pipeline().addBefore(ctx.name(), JsonObjectDecoder.class.getName(), new JsonObjectDecoder());
		}
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws IOException {
		byte[] byteArray = new byte[in.readableBytes()];
		in.readBytes(byteArray);
		out.add(objectMapper.readTree(byteArray));
	}

}

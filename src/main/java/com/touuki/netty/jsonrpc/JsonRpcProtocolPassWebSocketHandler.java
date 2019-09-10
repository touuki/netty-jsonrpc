package com.touuki.netty.jsonrpc;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.json.JsonObjectDecoder;
import io.netty.handler.timeout.IdleStateEvent;

@Sharable
public class JsonRpcProtocolPassWebSocketHandler extends MessageToMessageCodec<TextWebSocketFrame, JsonRpcObject> {

	private final JsonRpcClientHandler jsonRpcClientHandler;
	private final JsonRpcServerHandler jsonRpcServerHandler;
	
	public JsonRpcProtocolPassWebSocketHandler(JsonRpcClientHandler jsonRpcClientHandler) {
		this(jsonRpcClientHandler, null);
	}
	
	public JsonRpcProtocolPassWebSocketHandler(JsonRpcServerHandler jsonRpcServerHandler) {
		this(null, jsonRpcServerHandler);
	}
	
	public JsonRpcProtocolPassWebSocketHandler(JsonRpcClientHandler jsonRpcClientHandler, JsonRpcServerHandler jsonRpcServerHandler) {
		this.jsonRpcClientHandler = jsonRpcClientHandler;
		this.jsonRpcServerHandler = jsonRpcServerHandler;
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		ChannelPipeline cp = ctx.pipeline();
		if (cp.get(JsonNodeToJsonRpcObjectDecoder.class) == null) {
			cp.addAfter(ctx.name(), JsonNodeToJsonRpcObjectDecoder.class.getName(),
					new JsonNodeToJsonRpcObjectDecoder(jsonRpcClientHandler, jsonRpcServerHandler));
		}
	}
	
	
	@Override
	protected void decode(ChannelHandlerContext ctx, TextWebSocketFrame msg, List<Object> out)
			throws IOException {
		out.add(JsonUtils.MAPPER.readTree(msg.text()));
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent) {
			ctx.writeAndFlush(new PingWebSocketFrame()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
		} else {
			ctx.fireUserEventTriggered(evt);
		}
	}
	
	@Override
	protected void encode(ChannelHandlerContext ctx, JsonRpcObject msg, List<Object> out) throws JsonProcessingException {
		out.add(new TextWebSocketFrame(JsonUtils.MAPPER.writeValueAsString(msg)));
	}

}

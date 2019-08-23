package com.touuki.netty.websocket.jsonrpc;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleStateEvent;

@Sharable
public class JsonRpcTextFrameCodec extends MessageToMessageCodec<TextWebSocketFrame, JsonRpcObject> {
	public static final String JSONRPC_VERSION = "2.0";
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	protected void encode(ChannelHandlerContext ctx, JsonRpcObject msg, List<Object> out) throws JsonProcessingException {
		try {
			out.add(new TextWebSocketFrame(objectMapper.writeValueAsString(msg)));
		} catch (JsonProcessingException e) {
			if (msg instanceof JsonRpcResponse) {
				returnError(ctx, ((JsonRpcResponse) msg).getId(), JsonRpcException.INTERNAL_ERROR);
			} else if (msg instanceof JsonRpcRequest) {
				returnError(ctx, ((JsonRpcRequest) msg).getId(), JsonRpcException.INTERNAL_ERROR);
			}
			throw e;
		}
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, TextWebSocketFrame msg, List<Object> out) {
		Serializable id = null;
		try {
			JsonNode node;
			try {
				node = objectMapper.readTree(msg.text());
			} catch (IOException e) {
				throw JsonRpcException.PARSE_ERROR;
			}
			if (node.has("id") && !node.get("id").isNull()) {
				if (node.get("id").isTextual()) {
					id = node.get("id").textValue();
				} else if (node.get("id").isIntegralNumber()) {
					id = node.get("id").asLong();
				} else {
					throw JsonRpcException.INVALID_REQUEST;
				}
			}
			if (node.has("method")) {
				try {
					out.add(objectMapper.treeToValue(node, JsonRpcRequest.class));
				} catch (JsonProcessingException e) {
					throw JsonRpcException.INVALID_REQUEST;
				}
			} else if (node.has("error") || node.has("result")) {
				try {
					out.add(objectMapper.treeToValue(node, JsonRpcResponse.class));
				} catch (JsonProcessingException e) {
					throw JsonRpcException.INVALID_RESPONSE;
				}
			} else {
				throw JsonRpcException.INVALID_REQUEST;
			}
		} catch (JsonRpcException e) {
			returnError(ctx, id, e);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ctx.close();
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent) {
			ctx.writeAndFlush(new PingWebSocketFrame()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
		} else {
			super.userEventTriggered(ctx, evt);
		}
	}

	private void returnError(ChannelHandlerContext ctx, Serializable id, JsonRpcException jsonRpcException) {
		JsonRpcResponse jsonRpcResponse = new JsonRpcResponse(JSONRPC_VERSION, id, null, jsonRpcException);
		ctx.writeAndFlush(jsonRpcResponse).addListener((future) -> ctx.close());
	}
}

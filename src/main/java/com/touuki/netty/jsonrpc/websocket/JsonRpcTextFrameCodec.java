package com.touuki.netty.jsonrpc.websocket;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.touuki.netty.jsonrpc.JsonRpcException;
import com.touuki.netty.jsonrpc.JsonRpcObject;
import com.touuki.netty.jsonrpc.JsonRpcRequest;
import com.touuki.netty.jsonrpc.JsonRpcResponse;

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
	private static final Logger log = LoggerFactory.getLogger(JsonRpcTextFrameCodec.class);

	public static final String DEFAULT_JSONRPC_VERSION = "2.0";
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	protected void encode(ChannelHandlerContext ctx, JsonRpcObject msg, List<Object> out)
			throws JsonProcessingException {
		try {
			out.add(encode(msg));
		} catch (JsonProcessingException e) {
			if (msg instanceof JsonRpcResponse) {
				returnError(ctx, ((JsonRpcResponse) msg).getId(), JsonRpcException.INTERNAL_ERROR);
				log.error("Error during encode jsonrpc response: {}", msg, e);
			} else if (msg instanceof JsonRpcRequest) {
				returnError(ctx, ((JsonRpcRequest) msg).getId(), JsonRpcException.INTERNAL_ERROR);
				log.error("Error during encode jsonrpc request: {}", msg, e);
			}
			throw e;
		}
	}
	
	private TextWebSocketFrame encode(JsonRpcObject msg) throws JsonProcessingException {
		return new TextWebSocketFrame(objectMapper.writeValueAsString(msg));
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, TextWebSocketFrame msg, List<Object> out) throws JsonProcessingException {
		Object id = null;
		try {
			JsonNode node;
			try {
				node = objectMapper.readTree(msg.text());
			} catch (IOException e) {
				throw JsonRpcException.PARSE_ERROR;
			}
			id = parseId(node.get("id"));
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
					log.warn("Invalid response received: channel:{}; remoteAddress:{}; cause:{}",
							ctx.channel().id().asLongText(), ctx.channel().remoteAddress(), e.toString());
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
		log.error("Uncaught exception:", cause);
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

	private Object parseId(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isDouble()) {
			return node.asDouble();
		}
		if (node.isFloatingPointNumber()) {
			return node.asDouble();
		}
		if (node.isInt()) {
			return node.asInt();
		}
		if (node.isLong()) {
			return node.asLong();
		}
		if (node.isIntegralNumber()) {
			return node.asInt();
		}
		if (node.isTextual()) {
			return node.asText();
		}
		throw JsonRpcException.INVALID_REQUEST;
	}

	private void returnError(ChannelHandlerContext ctx, Object id, JsonRpcException jsonRpcException) throws JsonProcessingException {
		JsonRpcResponse jsonRpcResponse = new JsonRpcResponse(DEFAULT_JSONRPC_VERSION, id, null, jsonRpcException);
		ctx.writeAndFlush(encode(jsonRpcResponse)).addListener((future) -> ctx.close());
	}
}

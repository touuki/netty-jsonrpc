package com.touuki.netty.websocket.jsonrpc;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;



@SuppressWarnings({ "rawtypes", "unchecked" })
public class JsonRpcWebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
	private static final Logger log = LoggerFactory.getLogger(JsonRpcWebSocketHandler.class);
	// public static final ChannelGroup group = new
	// DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

	public static final AttributeKey<Map<Integer, Request>> REQUEST_ID_MAP = AttributeKey
			.valueOf("REQUEST_ID_MAP");
	public static final AttributeKey<AtomicInteger> REQUEST_NEXT_ID = AttributeKey.valueOf("REQUEST_NEXT_ID");

	public static final String JSONRPC_VERSION = "2.0";

	public static final int MAX_TIMEOUT_SECOND = 60;

	private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

	private JsonRpcWebSocketService jsonRpcWebSocketService;

	private static ObjectMapper objectMapper;

	public JsonRpcWebSocketHandler(JsonRpcWebSocketService jsonRpcWebSocketService) {
		this.jsonRpcWebSocketService = jsonRpcWebSocketService;
	}

	public static void setObjectMapper(ObjectMapper objectMapper) {
		JsonRpcWebSocketHandler.objectMapper = objectMapper;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
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
				if (!node.get("method").isTextual()) {
					throw JsonRpcException.INVALID_REQUEST;
				}
				JsonNode params;
				if (node.has("params")) {
					params = node.get("params");
				} else {
					params = NullNode.getInstance();
				}
				Object result = jsonRpcWebSocketService.handleRequest(ctx.channel(), node.get("method").textValue(),
						params);
				if (result == null) {
					result = "ok";
				}
				returnResult(ctx, id, result);
			} else if (node.has("error") && !node.get("error").isNull()) {
				// it's a response error
				if (id == null) {
					// If there was an error in detecting the id in the Request object (e.g. Parse
					// error/Invalid Request), it MUST be Null.
					log.warn("channel:{}; remoteAddress:{}; error:{}", ctx.channel().id().asLongText(),
							ctx.channel().remoteAddress(), createError(node.get("error")));
					ctx.close();
				} else {
					Request webSocketRequest = ctx.channel().attr(REQUEST_ID_MAP).get().remove(id);
					if (webSocketRequest != null) {
						webSocketRequest.getOnReply()
								.completeExceptionally(createError(node.get("error")).toException());
					}
				}
			} else if (node.has("result")) {
				// it's a response
				if (id == null) {
					throw JsonRpcException.INVALID_RESPONSE;
				} else {
					Request webSocketRequest = ctx.channel().attr(REQUEST_ID_MAP).get().remove(id);
					if (webSocketRequest != null) {
						webSocketRequest.getOnReply().complete(
								objectMapper.treeToValue(node.get("result"), webSocketRequest.getResponseType()));
					}
				}
			} else {
				throw JsonRpcException.INVALID_REQUEST;
			}
		} catch (JsonRpcException e) {
			returnError(ctx, id, e);
		}
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		ctx.flush();
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		ctx.channel().attr(REQUEST_ID_MAP).setIfAbsent(new ConcurrentHashMap<>());
		ctx.channel().attr(REQUEST_NEXT_ID).setIfAbsent(new AtomicInteger(1));
		// group.add(ctx.channel());
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		jsonRpcWebSocketService.channelInactive(ctx.channel());
		ctx.channel().attr(REQUEST_ID_MAP).set(null);
		ctx.channel().attr(REQUEST_NEXT_ID).set(null);
		// group.remove(ctx.channel());
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		try {
			cause.printStackTrace();
			returnError(ctx, null, JsonRpcException.INTERNAL_ERROR);
		} finally {
			ctx.close();
		}
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent) {
			ctx.writeAndFlush(new PingWebSocketFrame()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
		} else {
			super.userEventTriggered(ctx, evt);
		}
	}

	public static <T> CompletableFuture<T> sendRequest(Channel channel, String method, Object params,
			Class<T> responseType) throws JsonProcessingException {
		CompletableFuture<T> result = new CompletableFuture<>();
		// int requestId = ThreadLocalRandom.current().nextInt();
		int requestId = channel.attr(REQUEST_NEXT_ID).get().getAndIncrement();
		Map<String, Object> request = new HashMap<>();
		request.put("jsonrpc", JSONRPC_VERSION);
		request.put("id", requestId);
		request.put("method", method);
		request.put("params", params);
		channel.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(request)));
		channel.attr(REQUEST_ID_MAP).get().put(requestId, new Request(result, responseType));
		executor.schedule(() -> {
			Map<Integer, Request> map = channel.attr(REQUEST_ID_MAP).get(); // TODO 测试close以后是否会清除attr
			if (map != null) {
				map.remove(requestId);
			}
			result.completeExceptionally(new TimeoutException("Reach the max timeout limit."));
		}, MAX_TIMEOUT_SECOND, TimeUnit.SECONDS);
		return result;
	}

	public static ChannelFuture sendNotify(Channel channel, String method, Object params)
			throws JsonProcessingException {
		Map<String, Object> request = new HashMap<>();
		request.put("jsonrpc", JSONRPC_VERSION);
		request.put("method", method);
		request.put("params", params);
		return channel.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(request)));
	}

	public static ChannelGroupFuture sendNotify(ChannelGroup channelGroup, String method, Object params)
			throws JsonProcessingException {
		Map<String, Object> request = new HashMap<>();
		request.put("jsonrpc", JSONRPC_VERSION);
		request.put("method", method);
		request.put("params", params);
		return channelGroup.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(request)));
	}

	private void returnError(ChannelHandlerContext ctx, Serializable id, JsonRpcException jsonRpcException)
			throws JsonProcessingException {
		Map<String, Object> response = new HashMap<>();
		response.put("jsonrpc", JSONRPC_VERSION);
		response.put("error", jsonRpcException.toError());
		response.put("id", id);
		ctx.channel().writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(response)));
		if (jsonRpcException.isShouldClose()) {
			ctx.close();
		}
	}

	private void returnResult(ChannelHandlerContext ctx, Serializable id, Object result)
			throws JsonProcessingException {
		if (id != null) {
			Map<String, Object> response = new HashMap<>();
			response.put("jsonrpc", JSONRPC_VERSION);
			response.put("result", result);
			response.put("id", id);
			ctx.channel().writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(response)));
		}
	}

	private JsonRpcException.JsonRpcError createError(JsonNode jsonNode) throws JsonProcessingException {
		int code;
		String message = null;
		Object data = null;
		if (!jsonNode.has("code") || !jsonNode.get("code").isIntegralNumber()) {
			throw JsonRpcException.INVALID_RESPONSE;
		}
		code = jsonNode.get("code").asInt();
		if (jsonNode.has("message")) {
			if (!jsonNode.get("message").isTextual()) {
				throw JsonRpcException.INVALID_RESPONSE;
			}
			message = jsonNode.get("message").textValue();
		}
		if (jsonNode.has("data")) {
			data = objectMapper.treeToValue(jsonNode.get("data"), Object.class);
		}
		return new JsonRpcException.JsonRpcError(code, message, data);
	}

}
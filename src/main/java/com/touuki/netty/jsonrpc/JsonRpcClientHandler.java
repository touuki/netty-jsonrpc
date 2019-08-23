package com.touuki.netty.jsonrpc;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;

@Sharable
public class JsonRpcClientHandler extends SimpleChannelInboundHandler<JsonRpcResponse> {
	private static final Logger log = LoggerFactory.getLogger(JsonRpcClientHandler.class);

	public static final String JSONRPC_VERSION = "2.0";
	private static final AttributeKey<Map<Long, Request>> REQUEST_FOR_ID = AttributeKey.valueOf("REQUEST_FOR_ID");
	private static final AttributeKey<AtomicLong> REQUEST_NEXT_ID = AttributeKey.valueOf("REQUEST_NEXT_ID");
	private final ObjectMapper mapper;

	private int maxTimeoutSecond = 60;

	private final ScheduledExecutorService executor;

	public JsonRpcClientHandler(ObjectMapper mapper) {
		this(mapper, Executors.newScheduledThreadPool(1));
	}
	
	public JsonRpcClientHandler(ObjectMapper mapper, ScheduledExecutorService executor) {
		this.mapper = mapper;
		this.executor = executor;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, JsonRpcResponse msg) {
		if (msg.getError() == null) {
			// it's a response error
			if (msg.getId() == null) {
				// If there was an error in detecting the id in the Request object (e.g. Parse
				// error/Invalid Request), it MUST be Null.
				log.warn("Null id's error response received: channel:{}; remoteAddress:{}; error:{}",
						ctx.channel().id().asLongText(), ctx.channel().remoteAddress(),
						msg.getError().toDescribeString());
				ctx.close();
			} else {
				Request webSocketRequest = ctx.channel().attr(REQUEST_FOR_ID).get().remove(msg.getId());
				if (webSocketRequest != null) {
					webSocketRequest.getOnReply().completeExceptionally(msg.getError());
				}
			}
		} else {
			// it's a response
			if (msg.getId() == null) {
				log.warn("Null id's response received: channel:{}; remoteAddress:{}; response:{}",
						ctx.channel().id().asLongText(), ctx.channel().remoteAddress(), msg);
			} else {
				Request webSocketRequest = ctx.channel().attr(REQUEST_FOR_ID).get().remove(msg.getId());
				if (webSocketRequest != null) {
					try {
						Object result = mapper.treeToValue(msg.getResult(), webSocketRequest.getResponseType());
						webSocketRequest.getOnReply().complete(result);
					} catch (JsonProcessingException e) {
						webSocketRequest.getOnReply().completeExceptionally(e);
					}
				}
			}
		}
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		ctx.channel().attr(REQUEST_FOR_ID).setIfAbsent(new ConcurrentHashMap<>());
		ctx.channel().attr(REQUEST_NEXT_ID).setIfAbsent(new AtomicLong(0));
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		ctx.channel().attr(REQUEST_FOR_ID).set(null);
		ctx.channel().attr(REQUEST_NEXT_ID).set(null);
	}

	public <T> CompletableFuture<T> sendRequest(Channel channel, String method, Object params, Class<T> responseType) {
		CompletableFuture<T> result = new CompletableFuture<>();
		// int requestId = ThreadLocalRandom.current().nextInt();
		long requestId = channel.attr(REQUEST_NEXT_ID).get().getAndIncrement();

		channel.writeAndFlush(new JsonRpcRequest(JSONRPC_VERSION, requestId, method, mapper.valueToTree(params)));
		channel.attr(REQUEST_FOR_ID).get().put(requestId, new Request(result, responseType));
		executor.schedule(() -> {
			Map<Long, Request> map = channel.attr(REQUEST_FOR_ID).get(); // TODO 测试close以后是否会清除attr
			if (map != null) {
				map.remove(requestId);
			}
			result.completeExceptionally(new TimeoutException("Reach the max timeout limit."));
		}, maxTimeoutSecond, TimeUnit.SECONDS);
		return result;
	}

	public ChannelFuture sendNotify(Channel channel, String method, Object params) {
		return channel.writeAndFlush(new JsonRpcRequest(JSONRPC_VERSION, null, method, mapper.valueToTree(params)));
	}

	public ChannelGroupFuture sendNotify(ChannelGroup channelGroup, String method, Object params) {
		return channelGroup.writeAndFlush(new JsonRpcRequest(JSONRPC_VERSION, null, method, mapper.valueToTree(params)));
	}

}

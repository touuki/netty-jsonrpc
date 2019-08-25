package com.touuki.netty.jsonrpc;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;

public class JsonRpcClientInvocationHandler implements InvocationHandler {

	private final JsonRpcClientHandler client;

	JsonRpcClientInvocationHandler(JsonRpcClientHandler client) {
		this.client = client;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) {
		if (isDeclaringClassAnObject(method))
			return proxyObjectMethods(method, proxy, args);

		RequestData requestData = createRequestData(method, args);

		try {
			if (requestData.notification) {
				if (requestData.channelGroup != null) {
					client.sendNotification(requestData.channelGroup, requestData.methodName, requestData.arguments)
							.get();
					return null;
				} else if (requestData.channel != null) {
					client.sendNotification(requestData.channel, requestData.methodName, requestData.arguments).get();
					return null;
				} else {
					throw new ChannelNotFoundException("No proper channel found to send notification");
				}
			} else {
				if (requestData.channel != null) {
					CompletableFuture future = client.sendRequest(requestData.channel, requestData.methodName,
							requestData.arguments, method.getGenericReturnType());
					if (requestData.timeoutMillisecond >= 0) {
						return future.get(requestData.timeoutMillisecond, TimeUnit.MILLISECONDS);
					} else {
						return future.get();
					}
				} else {
					throw new ChannelNotFoundException("No proper channel found to send request");
				}
			}
		} catch (ExecutionException e) {
			if (e.getCause() != null) {
				if (e.getCause() instanceof JsonRpcException) {
					throw (JsonRpcException) e.getCause();
				} else if (e.getCause() instanceof TimeoutException) {
					throw new RuntimeException(e.getCause());
				} else {
					throw new RuntimeException(e.getCause());
				}
			} else {
				throw new RuntimeException(e);
			}
		} catch (InterruptedException | TimeoutException e) {
			throw new RuntimeException(e);
		}

	}

	private boolean isDeclaringClassAnObject(Method method) {
		return method.getDeclaringClass() == Object.class;
	}

	private Object proxyObjectMethods(Method method, Object proxyObject, Object[] args) {
		String name = method.getName();
		if (name.equals("toString")) {
			return proxyObject.getClass().getName() + "@" + System.identityHashCode(proxyObject);
		}
		if (name.equals("hashCode")) {
			return System.identityHashCode(proxyObject);
		}
		if (name.equals("equals")) {
			return proxyObject == args[0];
		}
		throw new RuntimeException(method.getName() + " is not a member of java.lang.Object");
	}

	private RequestData createRequestData(Method method, Object[] args) {

		RequestData requestData = new RequestData();
		JsonRpcMethod jsonRpcMethod = method.getAnnotation(JsonRpcMethod.class);
		JsonRpcRequestMode requestMode = JsonRpcRequestMode.AUTO;
		boolean paramsPassByObject = false;
		if (jsonRpcMethod == null) {
			requestData.methodName = method.getName();
			requestData.timeoutMillisecond = -1;
		} else {
			requestData.methodName = jsonRpcMethod.value();
			requestData.timeoutMillisecond = jsonRpcMethod.timeoutMilliseconds();
			requestMode = jsonRpcMethod.requestMode();
			paramsPassByObject = jsonRpcMethod.paramsPassByObject();
		}
		requestData.handleRequestMode(method, requestMode);
		requestData.handleParams(method, args, paramsPassByObject);
		return requestData;
	}

	private class RequestData {
		private Channel channel;
		private ChannelGroup channelGroup;
		private String methodName;
		private boolean notification;
		private Object arguments;
		private long timeoutMillisecond;

		private void handleRequestMode(Method method, JsonRpcRequestMode jsonRpcRequestMode) {
			switch (jsonRpcRequestMode) {
			case REQUEST:
				notification = false;
				break;
			case NOTIFICATION:
				notification = true;
				break;
			case AUTO:
			default:
				if (method.getReturnType() == Void.class) {
					notification = true;
				} else {
					notification = false;
				}
				break;
			}
		}

		private void handleParams(Method method, Object[] arguments, boolean paramsPassByObject) {
			Map<String, Object> argumentForName = new LinkedHashMap<>();

			Parameter[] parameters = method.getParameters();
			assert arguments.length == parameters.length;

			int i = 0;
			for (; i < parameters.length - 1; i++) {
				if (Channel.class.isAssignableFrom(parameters[i].getType())) {
					channel = (Channel) arguments[i];
					continue;
				} else if (ChannelGroup.class.isAssignableFrom(parameters[i].getType())) {
					channelGroup = (ChannelGroup) arguments[i];
					continue;
				}
				JsonRpcParam jsonRpcParam = parameters[i].getAnnotation(JsonRpcParam.class);
				if (jsonRpcParam != null) {
					argumentForName.put(jsonRpcParam.value(), arguments[i]);
				} else {
					argumentForName.put(parameters[i].getName(), arguments[i]);
				}
			}

			if (i < parameters.length) {
				String lastArgName;
				JsonRpcParam jsonRpcParam = parameters[i].getAnnotation(JsonRpcParam.class);
				if (jsonRpcParam != null) {
					lastArgName = jsonRpcParam.value();
				} else {
					lastArgName = parameters[i].getName();
				}
				if (method.isVarArgs() && !paramsPassByObject) {
					Class<?> componentType = parameters[i].getType().getComponentType();
					if (componentType.isPrimitive()) {
						Object array = arguments[i];
						int length = Array.getLength(array);
						for (int j = 0; j < length; j++) {
							argumentForName.put(lastArgName + "[" + j + "]", Array.get(array, j));
						}
					} else {
						Object[] array = (Object[]) arguments[i];
						for (int j = 0; j < array.length; j++) {
							argumentForName.put(lastArgName + "[" + j + "]", array[j]);
						}
					}
				} else {
					argumentForName.put(lastArgName, arguments[i]);
				}
			}

			if (paramsPassByObject) {
				this.arguments = argumentForName;
			} else {
				this.arguments = argumentForName.values();
			}
		}
	}
}

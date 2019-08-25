package com.touuki.netty.jsonrpc;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;

@Sharable
public class JsonRpcServerHandler extends SimpleChannelInboundHandler<JsonRpcRequest> {
	private static final Logger log = LoggerFactory.getLogger(JsonRpcServerHandler.class);

	public static final String DEFAULT_JSONRPC_VERSION = "2.0";
	private final ObjectMapper mapper;
	private final Class<?> remoteInterface;
	private final Object handler;
	private boolean shouldLogInvocationErrors = true;

	public JsonRpcServerHandler(ObjectMapper mapper, Object handler, Class<?> remoteInterface) {
		this.mapper = mapper;
		this.handler = handler;
		this.remoteInterface = remoteInterface;
		if (handler != null) {
			log.debug("created server for interface {} with handler {}", remoteInterface, handler.getClass());
		}
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, JsonRpcRequest msg) {
		String jsonrpc;
		if (msg.getJsonrpc() != null) {
			jsonrpc = msg.getJsonrpc();
		} else {
			jsonrpc = DEFAULT_JSONRPC_VERSION;
		}

		final String partialMethodName = getMethodName(msg.getMethod());
		final String serviceName = getServiceName(msg.getMethod());

		Set<Method> methods = findMatchingMethodsByName(getHandlerInterfaces(serviceName), partialMethodName);
		if (methods.isEmpty()) {
			returnError(ctx, jsonrpc, msg.getId(), JsonRpcException.METHOD_NOT_FOUND);
			return;
		}
		MethodInfo methodInfo = findMatchingMethodByParams(methods, msg.getParams());
		if (methodInfo == null) {
			returnError(ctx, jsonrpc, msg.getId(), JsonRpcException.METHOD_PARAMS_INVALID);
			return;
		}

		try {
			Object target = getHandler(serviceName);

			JsonNode result = invoke(target, methodInfo.method, methodInfo.arguments, methodInfo.channelParamsIndex,
					ctx.channel());

			if (msg.getId() != null) {
				if (result == null) {
					result = NullNode.getInstance();
				}
				ctx.writeAndFlush(new JsonRpcResponse(jsonrpc, msg.getId(), result, null));
			}
		} catch (Throwable e) {
			handleError(ctx, msg.getId(), jsonrpc, e);
		}

	}

	private Set<Method> findMatchingMethodsByName(Class<?>[] classes, String name) {
		Set<Method> methods = new HashSet<>();
		for (Class<?> clazz : classes) {
			for (Method method : clazz.getMethods()) {
				JsonRpcMethod jsonRpcMethod = method.getAnnotation(JsonRpcMethod.class);
				if (jsonRpcMethod != null) {
					if (jsonRpcMethod.required()) {
						if (jsonRpcMethod.value().equals(name)) {
							methods.add(method);
						}
					} else if (jsonRpcMethod.value().equals(name) || method.getName().equals(name)) {
						methods.add(method);
					}
				} else if (method.getName().equals(name)) {
					methods.add(method);
				}
			}
		}
		return methods;
	}

	private void returnError(ChannelHandlerContext ctx, String jsonrpc, Object id, JsonRpcException jsonRpcException) {
		if (id != null) {
			ctx.writeAndFlush(new JsonRpcResponse(jsonrpc, id, null, jsonRpcException));
		}
	}

	private void handleError(ChannelHandlerContext ctx, Object id, String jsonrpc, Throwable e) {
		Throwable unwrappedException = getException(e);

		if (shouldLogInvocationErrors) {
			log.warn("Error in JSON-RPC Service", unwrappedException);
		}
		if (id != null) {
			// TODO custom error resolver
			returnError(ctx, jsonrpc, id, new JsonRpcException(-32001, unwrappedException));
		}
	}

	private Throwable getException(final Throwable thrown) {
		Throwable e = thrown;
		while (InvocationTargetException.class.isInstance(e)) {
			// noinspection ThrowableResultOfMethodCallIgnored
			e = InvocationTargetException.class.cast(e).getTargetException();
			while (UndeclaredThrowableException.class.isInstance(e)) {
				// noinspection ThrowableResultOfMethodCallIgnored
				e = UndeclaredThrowableException.class.cast(e).getUndeclaredThrowable();
			}
		}
		return e;
	}

	private boolean hasReturnValue(Method m) {
		return m.getGenericReturnType() != null;
	}

	/**
	 * Returns the handler's class or interfaces. The variable serviceName is
	 * ignored in this class.
	 *
	 * @param serviceName the optional name of a service
	 * @return the class
	 */
	protected Class<?>[] getHandlerInterfaces(final String serviceName) {
		if (remoteInterface != null) {
			return new Class<?>[] { remoteInterface };
		} else if (Proxy.isProxyClass(handler.getClass())) {
			return handler.getClass().getInterfaces();
		} else {
			return new Class<?>[] { handler.getClass() };
		}
	}

	/**
	 * Get the service name from the methodNode. In this class, it is always
	 * <code>null</code>. Subclasses may parse the methodNode for service name.
	 *
	 * @param methodName the JsonNode for the method
	 * @return the name of the service, or <code>null</code>
	 */
	protected String getServiceName(final String methodName) {
		return null;
	}

	/**
	 * Get the method name from the methodNode.
	 *
	 * @param methodName the JsonNode for the method
	 * @return the name of the method that should be invoked
	 */
	protected String getMethodName(final String methodName) {
		return methodName;
	}

	/**
	 * Get the handler (object) that should be invoked to execute the specified RPC
	 * method. Used by subclasses to return handlers specific to a service.
	 *
	 * @param serviceName an optional service name
	 * @return the handler to invoke the RPC call against
	 */
	protected Object getHandler(String serviceName) {
		return handler;
	}

	private JsonNode invoke(Object target, Method method, List<JsonNode> arguments, int channelParamsIndex,
			Channel channel)
			throws IOException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		log.debug("Invoking method: {} with args {}", method.getName(), arguments);

		Object[] convertedParams = new Object[method.getParameterCount()];
		Type[] parameterTypes = method.getGenericParameterTypes();
		int i = 0;
		for (; i < parameterTypes.length - 1; i++) {
			convertedParams[i] = convertJsonToParameter(arguments.get(i), parameterTypes[i]);
		}

		if (i < parameterTypes.length) {
			if (method.isVarArgs()) {
				if (parameterTypes.length > arguments.size()) {
					convertedParams[i] = convertJsonToParameter(mapper.createArrayNode(), parameterTypes[i]);
				} else if (parameterTypes.length == arguments.size()) {
					convertedParams[i] = convertJsonToParameter(arguments.get(i), parameterTypes[i]);
				} else {
					ArrayNode arrayNode = mapper.createArrayNode();
					for (int j = i; j < arguments.size(); j++) {
						arrayNode.add(arguments.get(j));
					}
					convertedParams[i] = convertJsonToParameter(arrayNode, parameterTypes[i]);
				}
			} else {
				convertedParams[i] = convertJsonToParameter(arguments.get(i), parameterTypes[i]);
			}
		}

		if (channelParamsIndex >= 0) {
			convertedParams[channelParamsIndex] = channel;
		}

		Object result = method.invoke(target, convertedParams);

		log.debug("Invoked method: {}, result {}", method.getName(), result);

		return hasReturnValue(method) ? mapper.valueToTree(result) : null;
	}

	private Object convertJsonToParameter(JsonNode jsonNode, Type parameterType) throws IOException {
		JsonParser paramJsonParser = mapper.treeAsTokens(jsonNode);
		JavaType paramJavaType = mapper.getTypeFactory().constructType(parameterType);

		return mapper.readerFor(paramJavaType).with(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
				.readValue(paramJsonParser);
	}

	private MethodInfo findMatchingMethodByParams(Set<Method> methods, JsonNode paramsNode) {
		if (paramsNode == null || paramsNode.isNull()) {
			return findArrayParamsMatchingMethod(methods, mapper.createArrayNode());
		}
		if (paramsNode.isArray()) {
			return findArrayParamsMatchingMethod(methods, (ArrayNode) paramsNode);
		} else if (paramsNode.isObject()) {
			// return findObjectParamsMatchingMethod(methods, collectFieldNames(paramsNode),
			// (ObjectNode) paramsNode);
			return null;
		} else {
			return null;
		}
	}

	private boolean isMatchingType(JsonNode node, Class<?> type) {
		if (node.isNull()) {
			return type.isPrimitive() ? false : true;
		}
		if (node.isTextual()) {
			return String.class.isAssignableFrom(type) || byteOrCharAssignable(type);
		}
		if (node.isNumber()) {
			return isNumericAssignable(type);
		}
		if (node.isArray()) {
			return type.isArray() ? node.size() == 0 || isMatchingType(node.get(0), type.getComponentType())
					: Collection.class.isAssignableFrom(type);
		}
		if (node.isBoolean()) {
			return boolean.class.isAssignableFrom(type) || Boolean.class.isAssignableFrom(type);
		}
		if (node.isObject() || node.isPojo()) {
			return !type.isPrimitive() && !String.class.isAssignableFrom(type) && !Number.class.isAssignableFrom(type)
					&& !Boolean.class.isAssignableFrom(type);
		}
		return false;
	}

	private boolean byteOrCharAssignable(Class<?> type) {
		return byte[].class.isAssignableFrom(type) || Byte[].class.isAssignableFrom(type)
				|| char[].class.isAssignableFrom(type) || Character[].class.isAssignableFrom(type);
	}

	private boolean isNumericAssignable(Class<?> type) {
		return Number.class.isAssignableFrom(type) || short.class.isAssignableFrom(type)
				|| int.class.isAssignableFrom(type) || long.class.isAssignableFrom(type)
				|| float.class.isAssignableFrom(type) || double.class.isAssignableFrom(type);
	}

	private MethodInfo findArrayParamsMatchingMethod(Set<Method> methods, ArrayNode paramNodes) {
		Set<MethodInfo> nonVarArgsMethodInfos = new HashSet<>();
		Set<MethodInfo> varArgsMethodInfos = new HashSet<>();
		outter: for (Method method : methods) {
			int channelParamsIndex = -1;
			List<Class<?>> parameterTypes = Arrays.asList(method.getParameterTypes());
			if (parameterTypes.isEmpty()) {
				if (paramNodes.size() == 0) {					
					MethodInfo methodInfo = new MethodInfo();
					methodInfo.channelParamsIndex = channelParamsIndex;
					methodInfo.method = method;
					nonVarArgsMethodInfos.add(methodInfo);
				}
				continue;
			}
			
			int i = 0, j = 0;
			for (; i < parameterTypes.size() - 1 && j < paramNodes.size(); i++) {
				if (Channel.class.isAssignableFrom(parameterTypes.get(i))) {
					channelParamsIndex = i;
				} else if (isMatchingType(paramNodes.get(j), parameterTypes.get(i))) {
					j++;
				} else {
					continue outter;
				}
			}

			if (Channel.class.isAssignableFrom(parameterTypes.get(i))) {
				channelParamsIndex = i;
				i++;
			}

			if (method.isVarArgs()) {
				if (i < parameterTypes.size() - 1 || j < paramNodes.size()
						&& !isMatchingType(paramNodes.get(j), parameterTypes.get(i).getComponentType())) {
					continue;
				}
				MethodInfo methodInfo = new MethodInfo();
				methodInfo.channelParamsIndex = channelParamsIndex;
				methodInfo.method = method;
				varArgsMethodInfos.add(methodInfo);
			} else {
				if (i < parameterTypes.size() - 1) {
					continue;
				} else if (i == parameterTypes.size() - 1) {
					if (j == paramNodes.size() - 1) {
						if (!isMatchingType(paramNodes.get(j), parameterTypes.get(i))) {
							continue;
						}
					} else {
						continue;
					}
				} else {
					if (j < paramNodes.size()) {
						continue;
					}
				}
				MethodInfo methodInfo = new MethodInfo();
				methodInfo.channelParamsIndex = channelParamsIndex;
				methodInfo.method = method;
				nonVarArgsMethodInfos.add(methodInfo);
			}

		}
		MethodInfo bestMethodInfo;
		if (nonVarArgsMethodInfos.size() == 0) {
			if (varArgsMethodInfos.size() == 0) {
				return null;
			} else {
				bestMethodInfo = varArgsMethodInfos.iterator().next();
			}
		} else {
			bestMethodInfo = nonVarArgsMethodInfos.iterator().next();
		}
		List<JsonNode> arguments = new ArrayList<>();
		for (int i = 0; i < paramNodes.size(); i++) {
			arguments.add(paramNodes.get(i));
		}
		if (bestMethodInfo.channelParamsIndex >= 0) {			
			arguments.add(bestMethodInfo.channelParamsIndex, NullNode.getInstance());
		}
		bestMethodInfo.arguments = arguments;
		return bestMethodInfo;
	}

	private class MethodInfo {
		private Method method;
		private int channelParamsIndex;
		private List<JsonNode> arguments;

	}

}

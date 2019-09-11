package com.touuki.netty.jsonrpc;

import java.lang.reflect.Proxy;

import io.netty.channel.Channel;

public class ProxyUtils {

	@SuppressWarnings("unchecked")
	public static <T> T createClientProxy(ClassLoader classLoader, Class<T> proxyInterface,
			JsonRpcClientHandler client) {
		return (T) Proxy.newProxyInstance(classLoader, new Class<?>[] { proxyInterface },
				new JsonRpcClientInvocationHandler(client));
	}
}

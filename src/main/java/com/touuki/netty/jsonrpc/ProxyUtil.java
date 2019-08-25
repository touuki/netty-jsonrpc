package com.touuki.netty.jsonrpc;

import java.lang.reflect.Proxy;

import io.netty.channel.Channel;

public class ProxyUtil {

	@SuppressWarnings("unchecked")
	public static <T> T createClientProxy(ClassLoader classLoader, Class<T> proxyInterface,
			JsonRpcClientHandler client, Channel defaultChannel) {
		return (T) Proxy.newProxyInstance(classLoader, new Class<?>[] { proxyInterface },
				new JsonRpcClientInvocationHandler(client, defaultChannel));
	}
}

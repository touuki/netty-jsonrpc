package com.touuki.netty.jsonrpc;

import java.lang.reflect.Proxy;

public class ProxyUtil {

	@SuppressWarnings("unchecked")
	public static <T> T createClientProxy(ClassLoader classLoader, Class<T> proxyInterface,
			JsonRpcClientHandler client) {
		return (T) Proxy.newProxyInstance(classLoader, new Class<?>[] { proxyInterface },
				new JsonRpcClientInvocationHandler(client));
	}
}

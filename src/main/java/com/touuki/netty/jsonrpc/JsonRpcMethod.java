package com.touuki.netty.jsonrpc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for annotating service methods as JsonRpc method by name.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonRpcMethod {

	/**
	 * @return the method's name.
	 */
	String value() default "";

	/**
	 * Used for server, if {@code true}, the Java method name will not be used to
	 * resolve rpc calls.
	 * 
	 * @return whether the {@link #value()} is required to match the method.
	 */
	boolean required() default false;

	/**
	 * Used for client, whether the method should receive a response from server.
	 * 
	 * @return the request mode.
	 */
	JsonRpcRequestMode requestMode() default JsonRpcRequestMode.AUTO;

	/**
	 * Used for client, the max time wait for the response. If negative, it will be
	 * {@link JsonRpcClientHandler#maxTimeoutSecond}
	 * 
	 * @return timeout in milliseconds.
	 */
	long timeoutMilliseconds() default -1;

	/**
	 * Used for client, if {@code true}, the parameters will pass by object,
	 * otherwise array.
	 * 
	 * @return whether the request parameters pass by object.
	 */
	boolean paramsPassByObject() default false;
}
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
	String value();

	JsonRpcRequestMode requestMode() default JsonRpcRequestMode.AUTO;

	long timeoutMilliseconds() default -1;
	
	boolean paramsPassByObject() default false;
	/**
	 * If {@code true}, the Java method name will not be used to resolve rpc calls.
	 * 
	 * @return whether the {@link #value()} is required to match the method.
	 */
	boolean required() default false;
}
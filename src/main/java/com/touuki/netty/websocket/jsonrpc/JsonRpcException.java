package com.touuki.netty.websocket.jsonrpc;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonIgnoreProperties({ "cause", "stackTrace", "localizedMessage", "suppressed" })
@JsonInclude(Include.ALWAYS)
public class JsonRpcException extends RuntimeException {
	private static final long serialVersionUID = -7965782582127898499L;

	public static final JsonRpcException PARSE_ERROR = new JsonRpcException("Parse error", -32700);
	public static final JsonRpcException INVALID_REQUEST = new JsonRpcException("Invalid Request", -32600);
	public static final JsonRpcException METHOD_NOT_FOUND = new JsonRpcException("Method not found", -32601);
	public static final JsonRpcException METHOD_PARAMS_INVALID = new JsonRpcException("Invalid params", -32602);
	public static final JsonRpcException INTERNAL_ERROR = new JsonRpcException("Internal error", -32603);

	public static final JsonRpcException INVALID_RESPONSE = new JsonRpcException("Invalid Response", -32099);

	public static final int CUSTOM_SERVER_ERROR_UPPER = -32000;
	public static final int CUSTOM_SERVER_ERROR_LOWER = -32096;

	private final int code;
	@JsonInclude(Include.NON_NULL)
	private final ErrorData data;

	private JsonRpcException(String message, int code) {
		super(message);
		this.code = code;
		this.data = null;
	}

	@JsonCreator
	private JsonRpcException(@JsonProperty("code") int code, @JsonProperty("message") String message,
			@JsonProperty("data") ErrorData data) {
		super(message);
		this.code = code;
		this.data = data;
	}

	public JsonRpcException(int code, String message) {
		super(message);
		if (code < CUSTOM_SERVER_ERROR_LOWER) {
			code = CUSTOM_SERVER_ERROR_LOWER;
		}
		if (code > CUSTOM_SERVER_ERROR_UPPER) {
			code = CUSTOM_SERVER_ERROR_UPPER;
		}
		this.code = code;
		this.data = null;
	}

	public JsonRpcException(int code, String message, Throwable cause) {
		super(message, cause);
		if (code < CUSTOM_SERVER_ERROR_LOWER) {
			code = CUSTOM_SERVER_ERROR_LOWER;
		}
		if (code > CUSTOM_SERVER_ERROR_UPPER) {
			code = CUSTOM_SERVER_ERROR_UPPER;
		}
		this.code = code;
		this.data = initData(cause);
	}

	public JsonRpcException(int code, Throwable cause) {
		super(cause);
		if (code < CUSTOM_SERVER_ERROR_LOWER) {
			code = CUSTOM_SERVER_ERROR_LOWER;
		}
		if (code > CUSTOM_SERVER_ERROR_UPPER) {
			code = CUSTOM_SERVER_ERROR_UPPER;
		}
		this.code = code;
		this.data = initData(cause);
	}

	public ErrorData initData(Throwable cause) {
		return new ErrorData(cause.getClass().getName(), cause.getMessage());
	}

	public int getCode() {
		return code;
	}

	public ErrorData getData() {
		return data;
	}

	@JsonInclude(Include.ALWAYS)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ErrorData {
		@JsonProperty("type_name")
		private final String typeName;
		private final String message;

		@JsonCreator
		public ErrorData(@JsonProperty("type_name") String typeName, @JsonProperty("message") String message) {
			this.typeName = typeName;
			this.message = message;
		}

		public String getTypeName() {
			return typeName;
		}

		public String getMessage() {
			return message;
		}

	}
}

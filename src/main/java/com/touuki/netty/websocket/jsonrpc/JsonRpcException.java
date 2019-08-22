package com.touuki.netty.websocket.jsonrpc;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public class JsonRpcException extends RuntimeException{
	private static final long serialVersionUID = -7965782582127898499L;
	
	public static final JsonRpcException PARSE_ERROR = new JsonRpcException(-32700, true, "Parse error");
	public static final JsonRpcException INVALID_REQUEST = new JsonRpcException(-32600, true, "Invalid Request");
	public static final JsonRpcException METHOD_NOT_FOUND = new JsonRpcException(-32601, false, "Method not found");
	public static final JsonRpcException METHOD_PARAMS_INVALID = new JsonRpcException(-32602, false, "Invalid params");
	public static final JsonRpcException INTERNAL_ERROR = new JsonRpcException(-32603, false, "Internal error");
	
	public static final JsonRpcException INVALID_RESPONSE = new JsonRpcException(-32099, true, "Invalid Response");
	public static final JsonRpcException UNAUTHENTICATED = new JsonRpcException(-32098, false, "Unauthenticated");
	public static final JsonRpcException AUTHENTICATION_MISMATCH = new JsonRpcException(-32097, true, "Authentication mismatch");
	
	public static final int CUSTOM_SERVER_ERROR_UPPER = -32000;
	public static final int CUSTOM_SERVER_ERROR_LOWER = -32096;
	
	private final int code;

	private final boolean shouldClose;

    private JsonRpcException(int code, boolean shouldClose, String message) {
        super(message);
        this.code = code;
        this.shouldClose = shouldClose;
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
        this.shouldClose = false;
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
        this.shouldClose = false;
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
        this.shouldClose = false;
    }
    
    public JsonRpcError toError() {
    	Map<String, Object> data = null;
		if (this.getCause() != null) {
			data = new HashMap<>();
			data.put("type_name", this.getCause().getClass().getName());
			data.put("message",this.getCause().getMessage());
		}
		return new JsonRpcError(code, getMessage(), data);
	}
    
    public boolean isShouldClose() {
		return shouldClose;
	}

	public int getCode() {
		return code;
	}

    public static class JsonRpcError{
		private int code;
    	@JsonInclude(Include.ALWAYS)
    	private String message;
    	@JsonInclude(Include.NON_NULL)
    	private Object data;
    	
    	public JsonRpcError(int code, String message, Object data) {
    		this.code = code;
    		this.message = message;
    		this.data = data;
    	}

    	public int getCode() {
			return code;
		}

		public void setCode(int code) {
			this.code = code;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public Object getData() {
			return data;
		}

		public void setData(Object data) {
			this.data = data;
		}

		public JsonRpcException toException() {
    		return new JsonRpcException(code, false, message);
    	}

		@Override
		public String toString() {
			return "JsonRpcError [code=" + code + ", message=" + message + ", data=" + data + "]";
		}
    }
}

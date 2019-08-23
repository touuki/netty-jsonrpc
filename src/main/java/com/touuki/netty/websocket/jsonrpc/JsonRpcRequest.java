package com.touuki.netty.websocket.jsonrpc;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(Include.NON_NULL)
public class JsonRpcRequest {
	private final String jsonrpc;
	private final Serializable id;
	private final String method;
	private final JsonNode params;

	@JsonCreator
	public JsonRpcRequest(@JsonProperty("jsonrpc") String jsonrpc, @JsonProperty("id") Serializable id,
			@JsonProperty("method") String method, @JsonProperty("params") JsonNode params) {
		this.jsonrpc = jsonrpc;
		this.id = id;
		this.method = method;
		this.params = params;
	}

	public String getJsonrpc() {
		return jsonrpc;
	}

	public Serializable getId() {
		return id;
	}

	public String getMethod() {
		return method;
	}

	public JsonNode getParams() {
		return params;
	}

	@Override
	public String toString() {
		return "JsonRpcRequest [jsonrpc=" + jsonrpc + ", id=" + id + ", method=" + method + ", params=" + params + "]";
	}
}

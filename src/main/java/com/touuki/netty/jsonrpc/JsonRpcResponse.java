package com.touuki.netty.jsonrpc;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class JsonRpcResponse {
	
	private final String jsonrpc;
	@JsonInclude(Include.ALWAYS)
	private final Serializable id;
	private final JsonNode result;
	private final JsonRpcException error;

	@JsonCreator
	public JsonRpcResponse(@JsonProperty("jsonrpc") String jsonrpc, @JsonProperty("id") Serializable id,
			@JsonProperty("result") JsonNode result, @JsonProperty("error") JsonRpcException error) {
		this.jsonrpc = jsonrpc;
		this.id = id;
		this.result = result;
		this.error = error;
	}

	public String getJsonrpc() {
		return jsonrpc;
	}

	public Serializable getId() {
		return id;
	}

	public JsonNode getResult() {
		return result;
	}

	public JsonRpcException getError() {
		return error;
	}

	@Override
	public String toString() {
		if (error == null) {			
			return "JsonRpcResponse [jsonrpc=" + jsonrpc + ", id=" + id + ", result=" + result + ", error=" + error + "]";
		} else {
			return "JsonRpcResponse [jsonrpc=" + jsonrpc + ", id=" + id + ", result=" + result + ", error=" + error.toDescribeString() + "]";
		}
	}
}

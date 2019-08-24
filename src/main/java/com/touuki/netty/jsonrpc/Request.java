package com.touuki.netty.jsonrpc;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

class Request {
    private CompletableFuture onReply;
    private Type responseType;

    public Request(CompletableFuture onReply, Type responseType) {
        this.onReply = onReply;
        this.responseType = responseType;
    }

    public CompletableFuture getOnReply() {
        return onReply;
    }

    public Type getResponseType() {
        return responseType;
    }
}

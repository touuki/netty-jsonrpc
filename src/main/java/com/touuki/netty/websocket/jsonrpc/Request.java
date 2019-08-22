package com.touuki.netty.websocket.jsonrpc;

import java.util.concurrent.CompletableFuture;

class Request<T> {
    private CompletableFuture<T> onReply;
    private Class<T> responseType;

    public Request(CompletableFuture<T> onReply, Class<T> responseType) {
        this.onReply = onReply;
        this.responseType = responseType;
    }

    public CompletableFuture<T> getOnReply() {
        return onReply;
    }

    public Class<T> getResponseType() {
        return responseType;
    }
}

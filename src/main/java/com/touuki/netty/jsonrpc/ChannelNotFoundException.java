package com.touuki.netty.jsonrpc;

public class ChannelNotFoundException extends RuntimeException{

	private static final long serialVersionUID = -2851672142671545559L;

	public ChannelNotFoundException() {
        super();
    }

    public ChannelNotFoundException(String message) {
        super(message);
    }

    public ChannelNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChannelNotFoundException(Throwable cause) {
        super(cause);
    }

}

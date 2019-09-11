# netty-jsonrpc
A high performance asynchronous [JSON-RPC](https://www.jsonrpc.org) protocol Java implementation based on [Netty](https://netty.io), can either work on a raw tcp or a websocket. A node can be both JSON-RPC server and client in the same channel.

## Usage
### In Spring
create a client interface

```java
public interface DemoClient {

	@JsonRpcMethod(value = "img.data")
	byte[] requestWithCustomMethodName(Channel channel);

	// to send a request, there must be a Channel in the parameters, and it will be
	// removed from the parameter array when sending.
	String freeChannelIndex(int param1, long param2, Channel channel, String param3);

	@JsonRpcMethod(timeoutMilliseconds = 5000L)
	Map<String, Object> requestForCustomTimeout(Channel channel);

	boolean requestForVarArgs(Channel channel, String... params);

	@JsonRpcMethod(requestMode = JsonRpcRequestMode.REQUEST)
	void requestForWhetherExceptionOccur(Channel channel);

	void notifyForChannel(Channel channel, Map<String, Object> param1, int... params);

	// group can only be used for sending notification.
	void notifyForGroup(ChannelGroup channelGroup, String param1, long param2);

	@JsonRpcMethod(requestMode = JsonRpcRequestMode.NOTIFICATION)
	Object notifyIfDontCareAboutResult(Channel channel, Date param1);

	boolean requestForOverload(Channel channel, int param1);

	boolean requestForOverload(Channel channel, int param1, Boolean param2);

	boolean requestForOverload(Channel channel, int param1, Boolean param2, Object... params);
}
```
create a server interface

```java
public interface DemoServer {

	@JsonRpcMethod(value = "img.data", required = true)
	byte[] requestWithCustomMethodName();

	// to get the channel info, you just need to add a Channel class parameter in
	// the parameters, the index can be any.
	String freeChannelIndex(int param1, long param2, Channel channel, String param3);

	boolean requestForVarArgs(Map<String, Object> param1, String... params);

	boolean requestForOverload(int param1);

	boolean requestForOverload(Channel channel, int param1, Boolean param2);

	boolean requestForOverload(int param1, Boolean param2, Object... params);
}
```
implement it

```java
@Component
public class DemoServerImpl implements DemoServer{

	@Override
	public byte[] requestWithCustomMethodName() {
		...
	}
	...
```
Beans

```java
@Bean
public JsonRpcClientHandler jsonRpcClientHandler(ObjectMapper objectMapper) {
	return new JsonRpcClientHandler(objectMapper);
}

@Bean
public JsonRpcServerHandler jsonRpcServerHandler(ObjectMapper objectMapper) {
	return new JsonRpcServerHandler(objectMapper, new DemoServerImpl(), DemoServer.class);
}

@Bean
public DemoClient demoClient(JsonRpcClientHandler jsonRpcClientHandler) {
	return ProxyUtils.createClientProxy(DemoClient.class.getClassLoader(), DemoClient.class, jsonRpcClientHandler);
	}
```
Add to channel pipeline

```java
pipeline.addLast(new JsonRpcProtocolHandler(jsonRpcClientHandler, jsonRpcServerHandler));
```
or base on a websocket

```java
pipeline.addLast(new JsonRpcProtocolPassWebSocketHandler(jsonRpcClientHandler, jsonRpcServerHandler));
```
request can also be sent directly by JsonRpcClientHandler and get a Future.

```java
jsonRpcClientHandler.sendRequest(channel, method, params, responseType);
jsonRpcClientHandler.sendNotification(channel, method, params);
jsonRpcClientHandler.sendNotification(channelGroup, method, params);
```
## TODO
1. reflect cache
2. params pass by object

## References
* [jsonrpc4j](https://github.com/briandilley/jsonrpc4j)

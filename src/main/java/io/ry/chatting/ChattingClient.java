

package io.ry.chatting;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.ry.chatting.ChatServiceGrpc;
import io.ry.chatting.LoginRequest;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ChattingClient {
  private static final Logger logger = Logger.getLogger(ChattingClient.class.getName());

  private final ManagedChannel channel;
  private final ChatServiceGrpc.ChatServiceBlockingStub blockingStub;

  /** Construct client connecting to HelloWorld server at {@code host:port}. */
  public ChattingClient(String host, int port) {
    this(ManagedChannelBuilder.forAddress(host, port)
        // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
        // needing certificates.
        .usePlaintext()
        .build());
  }

  /** Construct client for accessing HelloWorld server using the existing channel. */
  ChattingClient(ManagedChannel channel) {
    this.channel = channel;
    blockingStub = ChatServiceGrpc.newBlockingStub(channel);
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  /** Say hello to server. */
  public void login(String name) {
    LoginRequest request = LoginRequest.newBuilder().setUser(name).build();
    LoginReply response;
    try {
      response = blockingStub.login(request);
      Boolean status = response.getSuccess();
      if (status) System.out.println("login Success");
      else {
        System.out.println("Login Failed");
        System.exit(0);
      }
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
      return;
    }
  }

  /**
   * Greet server. If provided, the first element of {@code args} is the name to use in the
   * greeting.
   */
  public static void main(String[] args) throws Exception {
    ChattingClient client = new ChattingClient("localhost", 50051);
    try {
      for (int i=0; i < 10;i++) {
        /* Access a service running on the local machine on port 50051 */
        String user = "world";
        if (args.length > 0) {
          user = args[0]; /* Use the arg as the name to greet if provided */
        }
        client.login(user);
      }} finally {
      client.shutdown();
    }
  }
}

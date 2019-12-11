

package io.ry.chatting;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.grpc.stub.StreamObserver;

public class ChattingClient {
  private static final Logger logger = Logger.getLogger(ChattingClient.class.getName());

  private final ManagedChannel channel;
  private final ChatServiceGrpc.ChatServiceBlockingStub blockingStub;
  private final ChatServiceGrpc.ChatServiceStub chatServiceStub;
  private String name = "";

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
    chatServiceStub = ChatServiceGrpc.newStub(channel);
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  /** Say hello to server. */
  public String  login() {
    Scanner scan = new Scanner(System.in);  //创建Scanner扫描器来封装System类的in输入流
    System.out.println("请输入用户名：");
    name = scan.nextLine();
    LoginRequest request = LoginRequest.newBuilder().setUser(name).build();
    LoginReply response;
    logger.info("user is " + name);
    try {
      response = blockingStub.login(request);
      Boolean status = response.getSuccess();
      if (status) System.out.println("login Success");
      else {
        System.out.println("Login Failed");
        System.exit(0);
      }
      return name;
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
      throw e;
    }
  }

  public StreamObserver<TalkRequest>  talk(final CountDownLatch done ) {

      ClientResponseObserver<TalkRequest, TalkReply> clientResponseObserver =
            new ClientResponseObserver<TalkRequest, TalkReply>() {

              ClientCallStreamObserver<TalkRequest> requestStream;

              @Override
              public void beforeStart(final ClientCallStreamObserver<TalkRequest> requestStream) {
                this.requestStream = requestStream;
                requestStream.disableAutoInboundFlowControl();

                // in a timely manor or else message processing throughput will suffer.
                requestStream.setOnReadyHandler(new Runnable() {
                  @Override
                  public void run() {
                    // Start generating values from where we left off on a non-gRPC thread.
                      while (requestStream.isReady()) {
                          //System.out.println("请回车确认开始聊天：");
                          TalkRequest request = TalkRequest.newBuilder().setUser(name).setContent("").build();
                          requestStream.onNext(request);
                          break;
                      }
                  }
                });
              }

              @Override
              public void onNext(TalkReply value) {
                  // logger.info("user is " + value.getUser() + ", msg is " + value.getContent());
                  if (!name.equals(value.getUser())) {
                      System.out.println(value.getUser() + "：" + value.getContent());
                  }
                  requestStream.request(1);
              }

              @Override
              public void onError(Throwable t) {
                t.printStackTrace();
                done.countDown();
              }

              @Override
              public void onCompleted() {
                logger.info("All Done");
                done.countDown();
              }
            };

      StreamObserver<TalkRequest> request = chatServiceStub.talk(clientResponseObserver);

      return request;

  }

  /**
   * Greet server. If provided, the first element of {@code args} is the name to use in the
   * greeting.
   */
  public static void main(String[] args) throws Exception {
    ChattingClient client = new ChattingClient("localhost", 50001);
      final CountDownLatch done = new CountDownLatch(1);
      try {
        /* Access a service running on the local machine on port 50051 */

        String name = client.login();
          StreamObserver<TalkRequest> requestStream = client.talk(done);
        while (true) {
            System.out.println("请输入消息：");
            Scanner scan = new Scanner(System.in);  //创建Scanner扫描器来封装System类的in输入流
            String msg = scan.nextLine();
            TalkRequest request = TalkRequest.newBuilder().setUser(name).setContent(msg).build();
            requestStream.onNext(request);
        }
      } finally {
      client.shutdown();
    }
  }
}



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

  public String login() {
    Scanner scan = new Scanner(System.in);  //创建Scanner扫描器来封装System类的in输入流
    System.out.println("请输入用户名：");
    name = scan.nextLine();
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
      return name;
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
      throw e;
    }
  }

  public StreamObserver<TalkRequest> talk() {

      ClientResponseObserver<TalkRequest, TalkReply> clientResponseObserver =
            new ClientResponseObserver<TalkRequest, TalkReply>() {

              ClientCallStreamObserver<TalkRequest> requestStream;

              @Override
              public void beforeStart(final ClientCallStreamObserver<TalkRequest> requestStream) {
                this.requestStream = requestStream;
                requestStream.disableAutoInboundFlowControl();

                requestStream.setOnReadyHandler(new Runnable() {
                  @Override
                  public void run() {
                    // Start generating values from where we left off on a non-gRPC thread.
                      while (requestStream.isReady()) {
                          TalkRequest request = TalkRequest.newBuilder().setUser(name).setContent("").setType(false).build();
                          requestStream.onNext(request);
                          break;
                      }
                  }
                });
              }

              @Override
              public void onNext(TalkReply value) {
                  if (!name.equals(value.getUser())) {
                      System.out.println(value.getUser() + "：" + value.getContent());
                  }
                  requestStream.request(1);
              }

              @Override
              public void onError(Throwable t) {
                t.printStackTrace();
              }

              @Override
              public void onCompleted() {
                logger.info("All Done");
              }
            };

      StreamObserver<TalkRequest> request = chatServiceStub.talk(clientResponseObserver);

      return request;

  }

  public static void main(String[] args) throws Exception {
    ChattingClient client = new ChattingClient("localhost", 50001);
      try {

        String name = client.login();
        StreamObserver<TalkRequest> requestStream = client.talk();
        while (true) {
            System.out.println("请输入消息：");
            Scanner scan = new Scanner(System.in);  //创建Scanner扫描器来封装System类的in输入流
            String msg = scan.nextLine();
            TalkRequest request = TalkRequest.newBuilder().setUser(name).setContent(msg).setType(true).build();
            requestStream.onNext(request);
        }
      } finally {
      client.shutdown();
    }
  }
}

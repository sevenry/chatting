
package io.ry.chatting;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.HashSet;
import java.util.logging.Logger;
import io.ry.chatting.ChatServiceGrpc;
import io.ry.chatting.LoginReply;
import io.ry.chatting.LoginRequest;

public class ChattingServer {
  private static final Logger logger = Logger.getLogger(ChattingServer.class.getName());
  private static HashSet<String> userList = new HashSet<String>();
  private Server server;

  private void start() throws IOException {
    /* The port on which the server should run */
    int port = 50051;
    server = ServerBuilder.forPort(port)
        .addService(new GreeterImpl())
        .build()
        .start();
    logger.info("Server started, listening on " + port);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        // Use stderr here since the logger may have been reset by its JVM shutdown hook.
        System.err.println("*** shutting down gRPC server since JVM is shutting down");
        ChattingServer.this.stop();
        System.err.println("*** server shut down");
      }
    });
  }

  private void stop() {
    if (server != null) {
      server.shutdown();
    }
  }

  /**
   * Await termination on the main thread since the grpc library uses daemon threads.
   */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  /**
   * Main launches the server from the command line.
   */
  public static void main(String[] args) throws IOException, InterruptedException {
    final ChattingServer server = new ChattingServer();
    server.start();
    server.blockUntilShutdown();
  }

  static class GreeterImpl extends ChatServiceGrpc.ChatServiceImplBase {

    @Override
    public void login(LoginRequest req, StreamObserver<LoginReply> responseObserver) {
      String user = req.getUser();
      boolean contain = userList.contains(user);
      userList.add(user);
      LoginReply reply = LoginReply.newBuilder().setSuccess(!contain).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

  }
}

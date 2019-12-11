
package io.ry.chatting;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import io.ry.chatting.ChatServiceGrpc;
import io.ry.chatting.LoginReply;
import io.ry.chatting.LoginRequest;

public class ChattingServer {

  private static final Logger logger = Logger.getLogger(ChattingServer.class.getName());

  private final int port;

  private final Server server;

  /** Create a RouteGuide server listening on {@code port} using {@code featureFile} database. */
  public ChattingServer(int port, Collection<TalkReply> replies) throws IOException {
    this(ServerBuilder.forPort(port), port, replies);
  }


  /** Create a RouteGuide server using serverBuilder as a base and features as data. */
  public ChattingServer(ServerBuilder<?> serverBuilder, int port, Collection<TalkReply> replies) {
    this.port = port;
    server = serverBuilder.addService(new ChattingService(replies))
            .build();
  }


  private static HashSet<String> userList = new HashSet<String>();

  private void start() throws IOException {
    /* The port on which the server should run */
    server.start();
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
    List<TalkReply> replies = new ArrayList<TalkReply>();

    ChattingServer server = new ChattingServer(50001, replies);
    server.start();
    server.blockUntilShutdown();
  }

  static class ChattingService extends ChatServiceGrpc.ChatServiceImplBase {
    private final Collection<TalkReply> replies;
    private final ConcurrentMap<TalkRequest, List<TalkReply>> routeNotes =
            new ConcurrentHashMap<TalkRequest, List<TalkReply>>();

    ChattingService(Collection<TalkReply> replies) {
      this.replies = replies;
    }

    private HashMap<String, StreamObserver<TalkReply> > records = new HashMap<String, StreamObserver<TalkReply> >();

    @Override
    public void login(LoginRequest req, StreamObserver<LoginReply> responseObserver) {
      String user = req.getUser();
      boolean contain = userList.contains(user);
      logger.info("login user is " + user);
      logger.info("contain is " + contain);
      userList.add(user);
      LoginReply reply = LoginReply.newBuilder().setSuccess(!contain).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<TalkRequest> talk(final StreamObserver<TalkReply> responseObserver) {
      // Set up manual flow control for the request stream. It feels backwards to configure the request
      // stream's flow control using the response stream's observer, but this is the way it is.
      final ServerCallStreamObserver<TalkReply> serverCallStreamObserver =
              (ServerCallStreamObserver<TalkReply>) responseObserver;
      serverCallStreamObserver.disableAutoInboundFlowControl();

      // execution.
      final AtomicBoolean wasReady = new AtomicBoolean(false);

      // message processing throughput will suffer.
      serverCallStreamObserver.setOnReadyHandler(new Runnable() {
        public void run() {
          if (serverCallStreamObserver.isReady() && wasReady.compareAndSet(false, true)) {
            logger.info("READY");
            // Signal the request sender to send one message. This happens when isReady() turns true, signaling that
            serverCallStreamObserver.request(1);
          }
        }
      });

      // Give gRPC a StreamObserver that can observe and process incoming requests.
      return new StreamObserver<TalkRequest>() {
        @Override
        public void onNext(TalkRequest request) {
          // Process the request and send a response or an error.
          try {
            // Accept and enqueue the request.
            String currentUser = request.getUser();
            if(!request.getType()) {
              records.put(currentUser, responseObserver);
            } else {
              String msg = request.getContent();
              logger.info("msg get from "+request.getUser() + ", content is " + request.getContent());
              TalkReply reply = TalkReply.newBuilder().setUser(currentUser).setContent(msg).build();

              for (String user: records.keySet()){
                  records.get(user).onNext(reply);
                }
            }

            // Check the provided ServerCallStreamObserver to see if it is still ready to accept more messages.
            if (serverCallStreamObserver.isReady()) {
              serverCallStreamObserver.request(1);
            } else {
              // If not, note that back-pressure has begun.
              wasReady.set(false);
            }
          } catch (Throwable throwable) {
            throwable.printStackTrace();
            responseObserver.onError(
                    Status.UNKNOWN.withDescription("Error handling request").withCause(throwable).asException());
          }
        }

        @Override
        public void onError(Throwable t) {
          t.printStackTrace();
          responseObserver.onCompleted();
        }

        @Override
        public void onCompleted() {
          logger.info("COMPLETED");
          responseObserver.onCompleted();
        }
      };

    };

  }
}

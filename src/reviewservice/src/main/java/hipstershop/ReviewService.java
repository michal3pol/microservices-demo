/*
 * Copyright 2018, Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hipstershop;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import hipstershop.Demo.Review;
import hipstershop.Demo.ReviewRequest;
import hipstershop.Demo.ReviewResponse;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.services.*;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ReviewService {

  private static final Logger logger = LogManager.getLogger(ReviewService.class);

  @SuppressWarnings("FieldCanBeLocal")
  private static int MAX_REVIEWS_TO_SERVE = 2;

  private Server server;
  private HealthStatusManager healthMgr;

  private static final ReviewService service = new ReviewService();

  private void start() throws IOException {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "9556"));
    healthMgr = new HealthStatusManager();

    server =
        ServerBuilder.forPort(port)
            .addService(new ReviewServiceImpl())
            .addService(healthMgr.getHealthService())
            .build()
            .start();
    logger.info("Review Service started, listening on " + port);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                  System.err.println(
                      "*** shutting down gRPC Reviews server since JVM is shutting down");
                  ReviewService.this.stop();
                  System.err.println("*** server shut down");
                }));
    healthMgr.setStatus("", ServingStatus.SERVING);
  }

  private void stop() {
    if (server != null) {
      healthMgr.clearStatus("");
      server.shutdown();
    }
  }

  private static class ReviewServiceImpl extends hipstershop.ReviewServiceGrpc.ReviewServiceImplBase {

    /**
     * Retrieves Reviews based on context provided in the request {@code ReviewRequest}.
     *
     * @param req the request containing context.
     * @param responseObserver the stream observer which gets notified with the value of {@code
     *     ReviewResponse}
     */
    @Override
    public void getReviews(ReviewRequest req, StreamObserver<ReviewResponse> responseObserver) {
      ReviewService service = ReviewService.getInstance();
      try {
        List<Review> allReviews = new ArrayList<>();
        logger.info("received Review request (context_words=" + req.getContextKeysList() + ")");
        if (req.getContextKeysCount() > 0) {
          for (int i = 0; i < req.getContextKeysCount(); i++) {
            Collection<Review> reviews = service.getReviewsByCategory(req.getContextKeys(i));
            allReviews.addAll(reviews);
          }
        } else {
          allReviews = service.getRandomReviews();
        }
        if (allReviews.isEmpty()) {
          // Serve random Reviews.
          allReviews = service.getRandomReviews();
        }
        ReviewResponse reply = ReviewResponse.newBuilder().addAllReviews(allReviews).build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
      } catch (StatusRuntimeException e) {
        logger.log(Level.WARN, "GetReviews Failed with status {}", e.getStatus());
        responseObserver.onError(e);
      }
    }
  }

  private static final ImmutableListMultimap<String, Review> reviewsMap = createReviewsMap();

  private Collection<Review> getReviewsByCategory(String category) {
    return reviewsMap.get(category);
  }

  private static final Random random = new Random();

  private List<Review> getRandomReviews() {
    List<Review> reviews = new ArrayList<>(MAX_REVIEWS_TO_SERVE);
    Collection<Review> allReviews = reviewsMap.values();
    for (int i = 0; i < MAX_REVIEWS_TO_SERVE; i++) {
      reviews.add(Iterables.get(allReviews, random.nextInt(allReviews.size())));
    }
    return reviews;
  }

  private static ReviewService getInstance() {
    return service;
  }

  /** Await termination on the main thread since the grpc library uses daemon threads. */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  private static ImmutableListMultimap<String, Review> createReviewsMap() {
    Review hairdryer =
        Review.newBuilder()
            .setRedirectUrl("/home")
            .setText("Nice product.")
            .build();
    Review tankTop =
        Review.newBuilder()
            .setRedirectUrl("/home")
            .setText("Bad product.")
            .build();
    Review candleHolder =
        Review.newBuilder()
            .setRedirectUrl("/home")
            .setText("Nice Candle holder!")
            .build();
    Review bambooGlassJar =
        Review.newBuilder()
            .setRedirectUrl("/home")
            .setText("Nice glass.")
            .build();
    Review watch =
        Review.newBuilder()
            .setRedirectUrl("/home")
            .setText("Now I know the time")
            .build();
    Review mug =
        Review.newBuilder()
            .setRedirectUrl("/home")
            .setText("Coffee from this mug is bad...")
            .build();
    Review loafers =
        Review.newBuilder()
            .setRedirectUrl("/home")
            .setText("ok")
            .build();
    return ImmutableListMultimap.<String, Review>builder()
        .putAll("clothing", tankTop)
        .putAll("accessories", watch)
        .putAll("footwear", loafers)
        .putAll("hair", hairdryer)
        .putAll("decor", candleHolder)
        .putAll("kitchen", bambooGlassJar, mug)
        .build();
  }

  private static void initStats() {
    if (System.getenv("DISABLE_STATS") != null) {
      logger.info("Stats disabled.");
      return;
    }
    logger.info("Stats enabled, but temporarily unavailable");

    long sleepTime = 10; /* seconds */
    int maxAttempts = 5;

    // TODO(arbrown) Implement OpenTelemetry stats

  }

  private static void initTracing() {
    if (System.getenv("DISABLE_TRACING") != null) {
      logger.info("Tracing disabled.");
      return;
    }
    logger.info("Tracing enabled but temporarily unavailable");
    logger.info("See https://github.com/GoogleCloudPlatform/microservices-demo/issues/422 for more info.");

    // TODO(arbrown) Implement OpenTelemetry tracing
    
    logger.info("Tracing enabled - Stackdriver exporter initialized.");
  }

  /** Main launches the server from the command line. */
  public static void main(String[] args) throws IOException, InterruptedException {

    new Thread(
            () -> {
              initStats();
              initTracing();
            })
        .start();

    // Start the RPC server. You shouldn't see any output from gRPC before this.
    logger.info("ReviewService starting.");
    final ReviewService service = ReviewService.getInstance();
    service.start();
    service.blockUntilShutdown();
  }
}

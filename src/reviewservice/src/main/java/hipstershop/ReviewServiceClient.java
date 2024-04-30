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

import hipstershop.Demo.Review;
import hipstershop.Demo.ReviewRequest;
import hipstershop.Demo.ReviewResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ReviewServiceClient {

  private static final Logger logger = LogManager.getLogger(ReviewServiceClient.class);

  private final ManagedChannel channel;
  private final hipstershop.ReviewServiceGrpc.ReviewServiceBlockingStub blockingStub;

  /** Construct client connecting to Ad Service at {@code host:port}. */
  private ReviewServiceClient(String host, int port) {
    this(
        ManagedChannelBuilder.forAddress(host, port)
            // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
            // needing certificates.
            .usePlaintext()
            .build());
  }

  /** Construct client for accessing RouteGuide server using the existing channel. */
  private ReviewServiceClient(ManagedChannel channel) {
    this.channel = channel;
    blockingStub = hipstershop.ReviewServiceGrpc.newBlockingStub(channel);
  }

  private void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  /** Get Ads from Server. */
  public void getReviews(String contextKey) {
    logger.info("Get Reviews with context " + contextKey + " ...");
    ReviewRequest request = ReviewRequest.newBuilder().addContextKeys(contextKey).build();
    ReviewResponse response;

    try {
      response = blockingStub.getReviews(request);
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARN, "RPC failed: " + e.getStatus());
      return;
    } 
    for (Review reviews : response.getReviewsList()) {
      logger.info("Reviews: " + reviews.getText());
    }
  }

  private static int getPortOrDefaultFromArgs(String[] args) {
    int portNumber = 9556;
    if (2 < args.length) {
      try {
        portNumber = Integer.parseInt(args[2]);
      } catch (NumberFormatException e) {
        logger.warn(String.format("Port %s is invalid, use default port %d.", args[2], 9556));
      }
    }
    return portNumber;
  }

  private static String getStringOrDefaultFromArgs(
      String[] args, int index, @Nullable String defaultString) {
    String s = defaultString;
    if (index < args.length) {
      s = args[index];
    }
    return s;
  }

  /**
   * Ads Service Client main. If provided, the first element of {@code args} is the context key to
   * get the ads from the Ads Service
   */
  public static void main(String[] args) throws InterruptedException {
    // Add final keyword to pass checkStyle.
    final String contextKeys = getStringOrDefaultFromArgs(args, 0, "camera");
    final String host = getStringOrDefaultFromArgs(args, 1, "localhost");
    final int serverPort = getPortOrDefaultFromArgs(args);

    ReviewServiceClient client = new ReviewServiceClient(host, serverPort);
    try {
      client.getReviews(contextKeys);
    } finally {
      client.shutdown();
    }

    logger.info("Exiting ReviewServiceClient...");
  }
}

/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.telemetry;

import com.newrelic.telemetry.http.HttpPoster;
import com.newrelic.telemetry.http.HttpResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Map;

/** Implementation of the HttpPoster interface using an Java 11 JDK Http client. */
public class Java11HttpPoster implements HttpPoster {
  private final HttpClient httpClient;

  /**
   * Create a Java11HttpPoster with your own object.
   *
   * @param httpClient - the preconstructed HTTP client object
   */
  public Java11HttpPoster(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  /**
   * Create a default Java11HttpPoster with a custom call timeout.
   *
   * @param callTimeout - the timeout for HTTP calls
   */
  public Java11HttpPoster(Duration callTimeout) {
    this.httpClient = HttpClient.newBuilder().connectTimeout(callTimeout).build();
  }

  /** Create a default Java11HttpPoster with a connect timeout set to 2 seconds. */
  public Java11HttpPoster() {
    this(Duration.ofSeconds(2));
  }

  @Override
  public HttpResponse post(URL url, Map<String, String> headers, byte[] body, String mediaType)
      throws IOException {

    try {
      var builder =
          HttpRequest.newBuilder(url.toURI()).POST(HttpRequest.BodyPublishers.ofByteArray(body));
      headers.forEach(builder::header);
      builder.header("Content-Type", mediaType);
      var req = builder.build();

      var response =
          sendWithRetry(
              req, java.net.http.HttpResponse.BodyHandlers.ofString(Charset.defaultCharset()));

      return toSdkResponse(response);
    } catch (URISyntaxException | InterruptedException e) {
      throw new IOException(e);
    }
  }

  /**
   * @param req a retryable request
   */
  private <T> java.net.http.HttpResponse<T> sendWithRetry(
      HttpRequest req,
      java.net.http.HttpResponse.BodyHandler<T> handler)
        throws IOException, InterruptedException {
    IOException first = null;
    int attempt = 0;
    for (;;) {
      try {
        return httpClient.send(req, handler);
      } catch (IOException e) {
        attempt++;
        if (attempt < 2 && isRetryable(e)) {
          if (first == null) {
            first = e;
          } else {
            first.addSuppressed(e);
          }
          continue;
        }

        if (first != null) {
          e.addSuppressed(first);
        }
        throw e;
      }
    }
  }

  private boolean isRetryable(Throwable t) {
    for (Throwable cause = t; cause != null; cause = cause.getCause()) {
      // Caused by: java.io.IOException: Connection reset by peer
      //  at java.base/sun.nio.ch.FileDispatcherImpl.read0(Native Method)
      if (cause.getClass() == IOException.class) {
        return true;
      }
    }
    return false;
  }

  public static HttpResponse toSdkResponse(java.net.http.HttpResponse actual) {
    return new HttpResponse(
        actual.body().toString(),
        actual.statusCode(),
        "" + actual.statusCode(),
        actual.headers().map());
  }

  public static MetricBatchSenderFactory metricSenderFactory() {
    return MetricBatchSenderFactory.fromHttpImplementation(Java11HttpPoster::new);
  }

  public static SpanBatchSenderFactory spanSenderFactory() {
    return SpanBatchSenderFactory.fromHttpImplementation(Java11HttpPoster::new);
  }

  public static EventBatchSenderFactory eventSenderFactory() {
    return EventBatchSenderFactory.fromHttpImplementation(Java11HttpPoster::new);
  }
}

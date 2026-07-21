package com.loadlab.worker;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class WebClientConfig {

  @Bean
  public WebClient loadGenWebClient(
      @Value("${webclient.max-connections:2000}") int maxConnections) {
    // This is the NEW resource ceiling — it replaces the old "number of OS threads".
    // 2000 concurrent requests here cost a handful of event-loop threads, not 2000
    // separate system threads as the thread-per-VU model did.
    ConnectionProvider provider =
        ConnectionProvider.builder("load-gen-pool")
            .maxConnections(maxConnections)
            .pendingAcquireTimeout(Duration.ofSeconds(10))
            .build();

    HttpClient httpClient =
        HttpClient.create(provider)
            .responseTimeout(Duration.ofSeconds(10))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);

    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
  }
}

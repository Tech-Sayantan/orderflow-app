package dev.tan.orderflow.orders;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpPricingClient implements PricingClient {

    private final RestClient client;

    public HttpPricingClient(
            @Value("${orderflow.pricing.url}") String baseUrl,
            @Value("${orderflow.pricing.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${orderflow.pricing.read-timeout-ms}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public PriceQuote quote(String sku) {
        try {
            PriceQuote quote = client.get().uri("/api/v1/prices/{sku}", sku)
                    .retrieve().body(PriceQuote.class);
            if (quote == null || quote.amount() == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Pricing response was empty");
            }
            return quote;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown SKU", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Pricing is unavailable", ex);
        }
    }
}

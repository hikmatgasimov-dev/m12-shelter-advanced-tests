
package lv.bootcamp.shelter.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ticket: EXT-1 (see README).
 * The real RestClientPartnerRegistryClient talks to a real local HTTP server
 * (MockWebServer) whose responses we script. Nothing in the client is mocked.
 */
class RestClientPartnerRegistryClientTest {

    private MockWebServer server;
    private RestClientPartnerRegistryClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new RestClientPartnerRegistryClient(RestClient.builder(), server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void lookup_shouldParseSuccessResponseAndCallTheRegistryEndpoint() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"registered": true, "partnerShelterName": "Riga Shelter"}
                        """));

        PartnerRegistryResult result = client.lookup("985112000123456");

        assertThat(result.registered()).isTrue();
        assertThat(result.partnerShelterName()).isEqualTo("Riga Shelter");

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/registry/985112000123456");
    }

    @Test
    void lookup_shouldParseResponseWhenChipIsNotRegisteredElsewhere() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"registered": false, "partnerShelterName": null}
                        """));

        PartnerRegistryResult result = client.lookup("985112000999999");

        assertThat(result.registered()).isFalse();
        assertThat(result.partnerShelterName()).isNull();
    }

    @Test
    void lookup_shouldThrowWhenPartnerRegistryFails() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> client.lookup("985112000123456"))
                .isInstanceOf(HttpServerErrorException.class)
                .hasMessageContaining("500");
    }
}
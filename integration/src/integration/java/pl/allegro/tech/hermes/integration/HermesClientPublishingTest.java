package pl.allegro.tech.hermes.integration;

import okhttp3.OkHttpClient;
import org.springframework.web.client.AsyncRestTemplate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import pl.allegro.tech.hermes.api.Topic;
import pl.allegro.tech.hermes.api.TopicName;
import pl.allegro.tech.hermes.client.HermesClient;
import pl.allegro.tech.hermes.client.HermesResponse;
import pl.allegro.tech.hermes.client.jersey.JerseyHermesSender;
import pl.allegro.tech.hermes.client.okhttp.OkHttpHermesSender;
import pl.allegro.tech.hermes.client.restTemplate.RestTemplateHermesSender;
import pl.allegro.tech.hermes.common.ssl.JvmKeystoreSslContextFactory;
import pl.allegro.tech.hermes.common.ssl.KeystoreProperties;
import pl.allegro.tech.hermes.common.ssl.SSLContextHolder;
import pl.allegro.tech.hermes.test.helper.message.TestMessage;

import javax.net.ssl.X509TrustManager;
import java.net.URI;

import static java.net.URI.create;
import static javax.ws.rs.client.ClientBuilder.newClient;
import static org.assertj.core.api.Assertions.assertThat;
import static pl.allegro.tech.hermes.client.HermesClientBuilder.hermesClient;
import static pl.allegro.tech.hermes.test.helper.builder.TopicBuilder.randomTopic;

public class HermesClientPublishingTest extends IntegrationTest {

    private TestMessage message = TestMessage.of("hello", "world");

    private URI topicURI = create("http://localhost:" + FRONTEND_PORT);
    private URI sslTopicUri = create("https://localhost:" + FRONTEND_SSL_PORT);

    private Topic topic;

    @BeforeMethod
    public void initialize() {
        topic = randomTopic("hermesClientGroup", "topic").build();
        operations.buildTopic(topic);
    }

    @Test
    public void shouldPublishUsingJerseyClient() {
        // given
        HermesClient client = hermesClient(new JerseyHermesSender(newClient())).withURI(topicURI).build();

        // when & then
        runTestSuiteForHermesClient(client);
    }

    @Test
    public void shouldPublishUsingRestTemplate() {
        // given
        HermesClient client = hermesClient(new RestTemplateHermesSender(new AsyncRestTemplate())).withURI(topicURI).build();

        // when & then
        runTestSuiteForHermesClient(client);
    }

    @Test
    public void shouldPublishUsingOkHttpClient() {
        // given
        HermesClient client = hermesClient(new OkHttpHermesSender(new OkHttpClient())).withURI(topicURI).build();

        // when & then
        runTestSuiteForHermesClient(client);
    }

    @Test
    public void shouldPublishUsingOkHttpClientUsingSSL() {
        // given
        HermesClient client = hermesClient(new OkHttpHermesSender(getOkHttpClientWithSslContextConfigured())).withURI(sslTopicUri).build();

        // when & then
        runTestSuiteForHermesClient(client);
    }

    @Test
    public void shouldCommunicateWithHermesUsingHttp2() {
        // given
        HermesClient client = hermesClient(new OkHttpHermesSender(getOkHttpClientWithSslContextConfigured())).withURI(sslTopicUri).build();

        // when
        HermesResponse response = client.publish(topic.getQualifiedName(), message.body()).join();

        // then
        assertThat(response.getProtocol()).isEqualTo("h2");
    }

    private void runTestSuiteForHermesClient(HermesClient client) {
        shouldPublishUsingHermesClient(client);
        shouldNotPublishUsingHermesClient(client);
    }

    private void shouldPublishUsingHermesClient(HermesClient client) {
        // when
        HermesResponse response = client.publish(topic.getQualifiedName(), message.body()).join();

        // then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessageId()).isNotEmpty();
        assertThat(response.getDebugLog()).contains("Sending message").contains("succeeded");
    }

    private void shouldNotPublishUsingHermesClient(HermesClient client) {
        // given
        TopicName topic = new TopicName("not", "existing");

        // when
        HermesResponse response = client.publish(topic.qualifiedName(), message.body()).join();

        // then
        assertThat(response.isFailure()).isTrue();
        assertThat(response.getDebugLog()).contains("Sending message").contains("failed");
    }

    private OkHttpClient getOkHttpClientWithSslContextConfigured() {
        KeystoreProperties keystore = new KeystoreProperties("classpath:client.keystore", "JKS", "password");
        KeystoreProperties truststore = new KeystoreProperties("classpath:client.truststore", "JKS", "password");
        JvmKeystoreSslContextFactory sslContextFactory = new JvmKeystoreSslContextFactory("TLS", keystore, truststore);
        SSLContextHolder sslContextHolder = sslContextFactory.create();
        return new OkHttpClient.Builder()
                .sslSocketFactory(
                        sslContextHolder.getSslContext().getSocketFactory(),
                        (X509TrustManager) sslContextHolder.getTrustManagers()[0]
                )
                .build();
    }
}

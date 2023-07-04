package hexlet.code;

import hexlet.code.domain.Url;
import hexlet.code.domain.UrlCheck;
import hexlet.code.domain.query.QUrl;
import hexlet.code.domain.query.QUrlCheck;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import io.javalin.Javalin;
import io.ebean.DB;
import io.ebean.Database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class AppTest {

    @Test
    void testInit() {
        assertThat(true).isEqualTo(true);
    }
    private static Javalin app;
    private static String baseUrl;
    private static Url existingUrl;
    private static Database database;
    private static MockWebServer mockServer;


    @BeforeAll
    public static void beforeAll() throws IOException {
        app = App.getApp();
        app.start(0);
        int port = app.port();
        baseUrl = "http://localhost:" + port;
        database = DB.getDefault();
        existingUrl = new Url("https://ya.ru");
        existingUrl.save();

        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterAll
    public static void afterAll() throws IOException {
        mockServer.shutdown();
        app.stop();
    }

    @BeforeEach
    void beforeEach() {
        database.script().run("/truncate.sql");
        database.script().run("/seed-test-db.sql");
    }

    @Nested
    class RootTest {

        @Test
        void testIndex() {
            HttpResponse<String> response = Unirest.get(baseUrl).asString();
            assertThat(response.getStatus()).isEqualTo(200);
        }
        @Test
        void testUrls() {
            HttpResponse<String> response = Unirest.get(baseUrl + "/urls").asString();
            assertThat(response.getStatus()).isEqualTo(200);
        }
        @Test
        void testNotValidUrl() {
            HttpResponse<String> response = Unirest.post(baseUrl + "/urls")
                    .field("name", "1231312").asString();
            assertThat(response.getStatus()).isEqualTo(302);
            assertThat(response.getHeaders().getFirst("Location")).isEqualTo("/");
            HttpResponse<String> response2 = Unirest
                    .get(baseUrl)
                    .asString();
            String body = response2.getBody();
            assertThat(body.contains("Некорректный URL"));
        }
    }

    @Nested
    class UrlTest {
        @Test
        void testNameUrls() {
            HttpResponse<String> response = Unirest
                    .get(baseUrl + "/urls")
                    .asString();
            String body = response.getBody();
            assertThat(body).contains(existingUrl.getName());
            assertThat(response.getStatus()).isEqualTo(200);
        }
        @Test
        void testCreateExistsUrl() {
            HttpResponse responsePost = Unirest
                    .post(baseUrl + "/urls")
                    .field("url", existingUrl.getName())
                    .asEmpty();
            assertThat(responsePost.getStatus()).isEqualTo(302);
            assertThat(responsePost.getHeaders().getFirst("Location")).isEqualTo("/urls");

            HttpResponse<String> response = Unirest
                    .get(baseUrl)
                    .asString();
            assertThat(response.getStatus()).isEqualTo(200);
        }
        @Test
        void testCheckUrl() throws IOException {

            String url = mockServer.url("").toString().replaceAll("/$", "");
            MockResponse mockedResponse = new MockResponse()
                    .setBody(Files.readString(Path.of("src/test/resources/test.html")));
            mockServer.enqueue(mockedResponse);

            Unirest
                    .post(baseUrl + "/urls")
                    .field("url", url)
                    .asEmpty();

            Url actualUrl = new QUrl()
                    .name.equalTo(url)
                    .findOne();
            assertThat(actualUrl).isNotNull();
            assertThat(actualUrl.getName()).isEqualTo(url);


            String urlCheck = "/urls/" + actualUrl.getId() + "/checks";

            HttpResponse response = Unirest
                    .post(baseUrl + urlCheck)
                    .asString();

            assertThat(response.getStatus()).isEqualTo(302);
            assertThat(response.getHeaders().getFirst("Location")).isEqualTo("/urls/" + actualUrl.getId());

            HttpResponse responseRedirected = Unirest
                    .get(baseUrl + "/urls/" + actualUrl.getId())
                    .asString();

            assertThat(responseRedirected.getStatus()).isEqualTo(200);

            UrlCheck actualCheckUrl = new QUrlCheck()
                    .url.equalTo(actualUrl)
                    .orderBy()
                    .createdAt.desc()
                    .findOne();

            assertThat(actualCheckUrl).isNotNull();
            assertThat(actualCheckUrl.getDescription()).contains("");
            assertThat(actualCheckUrl.getH1()).isEqualTo("Example Domain");
            assertThat(actualCheckUrl.getTitle()).isEqualTo("Example Domain");
        }

    }
}


package geektime.tdd.rest;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ASpike {
    private Server server;

    @BeforeEach
    public void start() throws Exception {
        server = new Server(8080);
        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);
        ServletContextHandler handler = new ServletContextHandler(server, "/hello");
        handler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.getWriter().write("test");
                resp.getWriter().flush();
            }
        }), "/world");
        server.setHandler(handler);
        server.start();
    }

    @AfterEach
    public void stop() throws Exception {
        server.stop();
    }

    @Test
    public void should() throws URISyntaxException, IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(new URI("http://localhost:8080/hello/world")).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("response.body() = " + response.body());
        assertEquals("test", response.body());
    }
}

package geektime.tdd.rest;

import com.tdd.di.ComponentRef;
import com.tdd.di.Config;
import com.tdd.di.Context;
import com.tdd.di.ContextConfig;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Stream;

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
        TestApplication application = new TestApplication();
        handler.addServlet(new ServletHolder(new ResourceServlet(application, new TestProviders(application))), "/world");
        server.setHandler(handler);
        server.start();
    }

    @AfterEach
    public void stop() throws Exception {
        server.stop();
    }

    static class ResourceServlet extends HttpServlet {
        private final Context context;
        private TestApplication application;
        private Providers providers;

        public ResourceServlet(TestApplication application, Providers providers) {
            this.application = application;
            this.providers = providers;
            context = application.getContext();
        }

        OutboundResponse dispatch(HttpServletRequest req, Stream<Class<?>> rootResources, ResourceContext rc) throws NoSuchMethodException {
            try {
                Class<?> rootClass = rootResources.findFirst().get();
                Object rootResource = rc.initResource(context.get(ComponentRef.of(rootClass)).get());
                Method method = Arrays.stream(rootClass.getMethods()).filter(m -> m.isAnnotationPresent(GET.class)).findFirst().get();
                Object result = method.invoke(rootResource);
                GenericEntity entity = new GenericEntity<>(result, method.getGenericReturnType());
                return new OutboundResponse() {
                    @Override
                    GenericEntity getGenericEntity() {
                        return entity;
                    }

                    @Override
                    Annotation[] getAnnotations() {
                        return new Annotation[0];
                    }

                    @Override
                    public int getStatus() {
                        return 0;
                    }

                    @Override
                    public StatusType getStatusInfo() {
                        return null;
                    }

                    @Override
                    public Object getEntity() {
                        return entity;
                    }

                    @Override
                    public <T> T readEntity(Class<T> entityType) {
                        return null;
                    }

                    @Override
                    public <T> T readEntity(GenericType<T> entityType) {
                        return null;
                    }

                    @Override
                    public <T> T readEntity(Class<T> entityType, Annotation[] annotations) {
                        return null;
                    }

                    @Override
                    public <T> T readEntity(GenericType<T> entityType, Annotation[] annotations) {
                        return null;
                    }

                    @Override
                    public boolean hasEntity() {
                        return false;
                    }

                    @Override
                    public boolean bufferEntity() {
                        return false;
                    }

                    @Override
                    public void close() {

                    }

                    @Override
                    public MediaType getMediaType() {
                        return null;
                    }

                    @Override
                    public Locale getLanguage() {
                        return null;
                    }

                    @Override
                    public int getLength() {
                        return 0;
                    }

                    @Override
                    public Set<String> getAllowedMethods() {
                        return null;
                    }

                    @Override
                    public Map<String, NewCookie> getCookies() {
                        return null;
                    }

                    @Override
                    public EntityTag getEntityTag() {
                        return null;
                    }

                    @Override
                    public Date getDate() {
                        return null;
                    }

                    @Override
                    public Date getLastModified() {
                        return null;
                    }

                    @Override
                    public URI getLocation() {
                        return null;
                    }

                    @Override
                    public Set<Link> getLinks() {
                        return null;
                    }

                    @Override
                    public boolean hasLink(String relation) {
                        return false;
                    }

                    @Override
                    public Link getLink(String relation) {
                        return null;
                    }

                    @Override
                    public Link.Builder getLinkBuilder(String relation) {
                        return null;
                    }

                    @Override
                    public MultivaluedMap<String, Object> getMetadata() {
                        return null;
                    }

                    @Override
                    public MultivaluedMap<String, String> getStringHeaders() {
                        return null;
                    }

                    @Override
                    public String getHeaderString(String name) {
                        return null;
                    }
                };
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        static abstract class OutboundResponse extends Response {
            abstract GenericEntity getGenericEntity();

            abstract Annotation[] getAnnotations();
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            try {
                Stream<Class<?>> rootResources = application.getClasses().stream().filter(c -> c.isAnnotationPresent(Path.class));
                ResourceContext rc = application.createResourceContext(req, resp);
                OutboundResponse result = dispatch(req, rootResources, rc);
                GenericEntity entity = (GenericEntity) result.getEntity();
                MessageBodyWriter<Object> writer = (MessageBodyWriter<Object>) providers.getMessageBodyWriter(entity.getRawType(), entity.getType(), result.getAnnotations(), result.getMediaType());
                writer.writeTo(result, entity.getRawType(), entity.getType(), result.getAnnotations(), result.getMediaType(), result.getHeaders(), resp.getOutputStream());
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class TestProviders implements Providers {
        private List<MessageBodyWriter> writers;
        private TestApplication application;

        public TestProviders(TestApplication application) {
            this.application = application;
            List<Class<?>> writerClasses = this.application.getClasses().stream().filter(c -> MessageBodyWriter.class.isAssignableFrom(c)).toList();
            Context context = application.getContext();
            writers = (List<MessageBodyWriter>) writerClasses.stream().map(c -> context.get(ComponentRef.of(c)).get()).toList();
        }

        @Override
        public <T> MessageBodyReader<T> getMessageBodyReader(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return null;
        }

        @Override
        public <T> MessageBodyWriter<T> getMessageBodyWriter(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return writers.stream().filter(w -> w.isWriteable(type, genericType, annotations, mediaType)).findFirst().get();
        }

        @Override
        public <T extends Throwable> ExceptionMapper<T> getExceptionMapper(Class<T> type) {
            return null;
        }

        @Override
        public <T> ContextResolver<T> getContextResolver(Class<T> contextType, MediaType mediaType) {
            return null;
        }
    }

    static class StringMessageBodyWriter implements MessageBodyWriter<String> {
        public StringMessageBodyWriter() {
        }

        @Inject
        @Named("prefix")
        String prefix;

        @Override
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return type == String.class;
        }

        @Override
        public void writeTo(String s, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                            MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
            PrintWriter writer = new PrintWriter(entityStream);
            writer.write(prefix);
            writer.write(s);
            writer.flush();
        }
    }

    static class TestApplication extends Application {

        private Context context;

        public ResourceContext createResourceContext(HttpServletRequest request, HttpServletResponse response) {
            return new ResourceContext() {
                @Override
                public <T> T getResource(Class<T> resourceClass) {
                    return null;
                }

                @Override
                public <T> T initResource(T resource) {
                    return resource;
                }
            };
        }

        public Context getContext() {
            return context;
        }

        public TestApplication() {
            ContextConfig config = new ContextConfig();
            config.from(getConfig());

            List<Class<?>> writerClasses = this.getClasses().stream().filter(c -> MessageBodyWriter.class.isAssignableFrom(c)).toList();

            for (Class writerClass : writerClasses) {
                config.component(writerClass, writerClass);
            }

            List<Class<?>> rootResources = this.getClasses().stream().filter(c -> c.isAnnotationPresent(Path.class)).toList();
            for (Class rootResource : rootResources) {
                config.component(rootResource, rootResource);
            }
            context = config.getContext();
        }

        public Config getConfig() {
            return new Config() {
                @Named("prefix")
                public String name = "prefix";
            };
        }

        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(TestResource.class, StringMessageBodyWriter.class);
        }
    }

    //status code, media type, headers, body
    @Path("/not-in-use-path")
    static class TestResource {
        public TestResource() {
        }

        @QueryParam("q")
        String q;

        @Inject
        @Named("prefix")
        String prefix;

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String get() {
            return prefix + "Hello World!";
        }

        @GET
        @Path("/with-headers")
        public Response withHeaders() {
            return Response.ok().header("Set-Cookie", new NewCookie.Builder("SESSION_ID")
                    .value("SID").build()).entity("string", new Annotation[0]).build();
        }

        @GET
        @Path("/generic")
        public GenericEntity<List<String>> generic() {
            return new GenericEntity<>(List.of("abc", "def")) {
            };
        }

        @GET
        @Path("/pojo-generic")
        public List<String> pojoGeneric() {
            return List.of("abc", "def");
        }

        @PUT
        public void update() {
        }
    }

    @Test
    public void should() throws URISyntaxException, IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(new URI("http://localhost:8080/hello/world")).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("response.body() = " + response.body());
        assertEquals("prefixprefixHello World!", response.body());
    }
}

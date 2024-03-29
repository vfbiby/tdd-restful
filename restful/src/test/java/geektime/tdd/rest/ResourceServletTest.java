package geektime.tdd.rest;

import jakarta.servlet.Servlet;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Providers;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResourceServletTest extends ServletTest {
    private Providers providers;
    private Runtime runtime;
    private ResourceRouter router;
    private ResourceContext resourceContext;
    private OutboundResponseBuilder response;

    @Override
    protected Servlet getServlet() {
        runtime = mock(Runtime.class);
        router = mock(ResourceRouter.class);
        resourceContext = mock(ResourceContext.class);
        providers = mock(Providers.class);

        when(runtime.getResourceRouter()).thenReturn(router);
        when(runtime.createResourceContext(any(), any())).thenReturn(resourceContext);
        when(runtime.getProviders()).thenReturn(providers);


        return new ResourceServlet(runtime);
    }

    @BeforeEach
    public void before() {
        RuntimeDelegate delegate = mock(RuntimeDelegate.class);
        RuntimeDelegate.setInstance(delegate);
        response = new OutboundResponseBuilder();

        when(delegate.createHeaderDelegate(eq(NewCookie.class))).thenReturn(new RuntimeDelegate.HeaderDelegate<>() {
            @Override
            public NewCookie fromString(String value) {
                return null;
            }

            @Override
            public String toString(NewCookie value) {
                return value.getName() + "=" + value.getValue();
            }
        });

    }

    // use status code as http status
    @Test
    @DisplayName("should use status from response")
    public void should_use_status_from_response() throws Exception {
        response.status(Response.Status.NOT_MODIFIED).returnFrom(router);
        HttpResponse<String> httpResponse = get("/hello/world");
        assertEquals(Response.Status.NOT_MODIFIED.getStatusCode(), httpResponse.statusCode());
    }

    // use headers as http headers
    @Test
    @DisplayName("should use http headers from response")
    public void should_use_http_headers_from_response() throws Exception {
        response.headers("Set-Cookie", new NewCookie.Builder("SESSION_ID").value("session").build(),
                new NewCookie.Builder("USER_ID").value("user").build()).returnFrom(router);

        HttpResponse<String> httpResponse = get("/hello/world");
        assertArrayEquals(new String[]{"SESSION_ID=session", "USER_ID=user"}, httpResponse.headers().allValues("Set-Cookie").toArray(String[]::new));
    }


    // writer body using MessageBodyWriter
    @Test
    @DisplayName("should write entity to http response using message body")
    public void should_write_entity_to_http_response_using_message_body() throws Exception {
        response.entity(new GenericEntity<>("entity", String.class), new Annotation[0]).returnFrom(router);
        HttpResponse<String> httpResponse = get("/hello/world");
        assertEquals("entity", httpResponse.body());
    }

    // throw WebApplicationException with response, use response
    @Test
    @DisplayName("should use response from web application exception")
    public void should_use_response_from_web_application_exception() throws Exception {
        response.status(Response.Status.FORBIDDEN)
                .headers(HttpHeaders.SET_COOKIE, new NewCookie.Builder("SESSION_ID").value("session").build())
                .entity(new GenericEntity<>("error", String.class), new Annotation[0])
                .throwFrom(router);
        HttpResponse<String> httpResponse = get("/hello/world");
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode());
        assertArrayEquals(new String[]{"SESSION_ID=session"}, httpResponse.headers().allValues(HttpHeaders.SET_COOKIE).toArray(String[]::new));
        assertEquals("error", httpResponse.body());
    }

    // throw other exception, use ExceptionMapper build response
    @Test
    @DisplayName("should build response by exception mapper if null response from web application exception")
    public void should_build_response_by_exception_mapper_if_null_response_from_web_application_exception() throws Exception {
        when(router.dispatch(any(), eq(resourceContext))).thenThrow(RuntimeException.class);
        when(providers.getExceptionMapper(eq(RuntimeException.class))).thenReturn(exception -> response.status(Response.Status.FORBIDDEN).build());
        HttpResponse<String> httpResponse = get("/hello/world");
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode());
    }

    // entity is null, ignore MessageBodyWriter
    @Test
    @DisplayName("should not call message body writer if entity is null")
    public void should_not_call_message_body_writer_if_entity_is_null() throws Exception {
        response.entity(null, new Annotation[0]).returnFrom(router);
        HttpResponse<String> httpResponse = get("/hello/world");
        assertEquals(Response.Status.OK.getStatusCode(), httpResponse.statusCode());
        assertEquals("", httpResponse.body());
    }

    // TODO: 2022/6/19 500 if MessageBodyWriter not found
    // TODO: 2022/6/22 500 if header delegate
    // TODO: 2022/6/22 500 if exception mapper

    // TODO: 2022/6/22 exception mapper
    @Test
    @DisplayName("should use response from web application exception thrown by exception mapper")
    public void should_use_response_from_web_application_exception_thrown_by_exception_mapper() throws Exception {
        when(router.dispatch(any(), eq(resourceContext))).thenThrow(RuntimeException.class);
        when(providers.getExceptionMapper(eq(RuntimeException.class))).thenReturn(exception -> {
            throw new WebApplicationException(response.status(Response.Status.FORBIDDEN).build());
        });
        HttpResponse<String> httpResponse = get("/hello/world");
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode());
    }

    @Test
    @DisplayName("should map exception thrown by exception mapper")
    public void should_map_exception_thrown_by_exception_mapper() throws Exception {
        when(router.dispatch(any(), eq(resourceContext))).thenThrow(RuntimeException.class);
        when(providers.getExceptionMapper(eq(RuntimeException.class))).thenReturn(exception -> {
            throw new IllegalArgumentException();
        });
        when(providers.getExceptionMapper(eq(IllegalArgumentException.class))).thenReturn(
                exception -> response.status(Response.Status.FORBIDDEN).build());
        HttpResponse<String> httpResponse = get("/hello/world");
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode());
    }

    // TODO: 2022/6/22 providers gets exception mapper
    // TODO: 2022/6/22 runtime delegate
    // TODO: 2022/6/22 header delegate
    // TODO: 2022/6/22 providers gets message body writer
    // TODO: 2022/6/22 message body writer write

    class OutboundResponseBuilder {
        Response.Status status = Response.Status.OK;
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        GenericEntity<String> entity = new GenericEntity<>("entity", String.class);
        Annotation[] annotations = new Annotation[0];
        MediaType mediaType = MediaType.TEXT_PLAIN_TYPE;

        public OutboundResponseBuilder headers(String name, Object... values) {
            headers.addAll(name, values);
            return this;
        }

        public OutboundResponseBuilder status(Response.Status status) {
            this.status = status;
            return this;
        }

        public OutboundResponseBuilder entity(GenericEntity<String> entity, Annotation[] annotations) {
            this.entity = entity;
            this.annotations = annotations;
            return this;
        }

        public void returnFrom(ResourceRouter router) {
            build(response -> when(router.dispatch(any(), eq(resourceContext))).thenReturn(response));
        }

        public void build(Consumer<OutboundResponse> consumer) {
            OutboundResponse response = build();
            consumer.accept(response);
        }

        private OutboundResponse build() {
            OutboundResponse response = mock(OutboundResponse.class);
            when(response.getStatus()).thenReturn(status.getStatusCode());
            when(response.getStatusInfo()).thenReturn(status);
            when(response.getHeaders()).thenReturn(headers);
            when(response.getGenericEntity()).thenReturn(entity);
            when(response.getAnnotations()).thenReturn(annotations);
            when(response.getMediaType()).thenReturn(mediaType);
            stubMessageBodyWriter();
            return response;
        }

        private void stubMessageBodyWriter() {
            when(providers.getMessageBodyWriter(eq(String.class), eq(String.class), same(annotations), eq(mediaType))).thenReturn(new MessageBodyWriter<>() {
                @Override
                public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
                    return false;
                }

                @Override
                public void writeTo(String s, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
                    PrintWriter writer = new PrintWriter(entityStream);
                    writer.write(s);
                    writer.flush();
                }
            });
        }

        public void throwFrom(ResourceRouter router) {
            build(response -> {
                WebApplicationException exception = new WebApplicationException(response);
                when(router.dispatch(any(), eq(resourceContext))).thenThrow(exception);
            });
        }
    }
}

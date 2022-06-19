package geektime.tdd.rest;

import jakarta.servlet.Servlet;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResourceServletTest extends ServletTest {
    private Runtime runtime;
    private ResourceRouter router;
    private ResourceContext resourceContext;

    @Override
    protected Servlet getServlet() {
        runtime = mock(Runtime.class);
        router = mock(ResourceRouter.class);
        resourceContext = mock(ResourceContext.class);

        when(runtime.getResourceRouter()).thenReturn(router);
        when(runtime.createResourceContext(any(), any())).thenReturn(resourceContext);

        return new ResourceServlet(runtime);
    }

    // use status code as http status
    @Test
    @DisplayName("should use status from response")
    public void should_use_status_from_response() throws Exception {
        OutboundResponse response = mock(OutboundResponse.class);
        when(response.getStatus()).thenReturn(Response.Status.NOT_MODIFIED.getStatusCode());
        when(router.dispatch(any(), eq(resourceContext))).thenReturn(response);
        when(response.getHeaders()).thenReturn(new MultivaluedHashMap<>());
        HttpResponse<String> httpResponse = get("/hello/world");
        assertEquals(Response.Status.NOT_MODIFIED.getStatusCode(), httpResponse.statusCode());
    }

    // use headers as http headers
    @Test
    @DisplayName("should use http headers from response")
    public void should_use_http_headers_from_response() throws Exception {
        OutboundResponse response = mock(OutboundResponse.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        RuntimeDelegate delegate = mock(RuntimeDelegate.class);
        RuntimeDelegate.setInstance(delegate);

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

        NewCookie sessionId = new NewCookie.Builder("SESSION_ID").value("session").build();
        NewCookie userId = new NewCookie.Builder("USER_ID").value("user").build();
        headers.addAll("Set-Cookie", sessionId, userId);

        when(response.getStatus()).thenReturn(Response.Status.NOT_MODIFIED.getStatusCode());
        when(response.getHeaders()).thenReturn(headers);
        when(router.dispatch(any(), eq(resourceContext))).thenReturn(response);

        HttpResponse<String> httpResponse = get("/hello/world");
        assertArrayEquals(new String[]{"SESSION_ID=session", "USER_ID=user"},
                httpResponse.headers().allValues("Set-Cookie").toArray(String[]::new));
    }

    // TODO: 2022/6/19 writer body using MessageBodyWriter
    // TODO: 2022/6/19 500 if MessageBodyWriter not found
    // TODO: 2022/6/19 throw WebApplicationException with response, use response
    // TODO: 2022/6/19 throw WebApplicationException with null response, use ExceptionMapper build response
    // TODO: 2022/6/19 throw other exception, use ExceptionMapper build response
}

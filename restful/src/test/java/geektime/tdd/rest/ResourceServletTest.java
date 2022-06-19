package geektime.tdd.rest;

import jakarta.servlet.Servlet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceServletTest extends ServletTest{
    @Override
    protected Servlet getServlet() {
        return new ResourceServlet();
    }

    @Test
    public void should_work(){
        assertTrue(true);
    }

    // TODO: 2022/6/19 use status code as http status
    // TODO: 2022/6/19 use headers as http headers
    // TODO: 2022/6/19 writer body using MessageBodyWriter
    // TODO: 2022/6/19 500 if MessageBodyWriter not found
    // TODO: 2022/6/19 throw WebApplicationException with response, use response
    // TODO: 2022/6/19 throw WebApplicationException with null response, use ExceptionMapper build response
    // TODO: 2022/6/19 throw other exception, use ExceptionMapper build response
}

# 40 | RESTFul Web Services (4): 在当前架构愿景下，要如何分解任务？

> 将需求分解为任务列表JAX-RS 的需求非常庞杂，根据前面我们介绍过的部分（参看第 36 讲），

主要的功能有这样几个方面：

- 将请求派分给对应的资源（Resource），并根据返回的状态、超媒体类型、内容，响应 Http 请求。
- 在处理请求派分时，可以支持多级子资源（Sub-Resource）。
- 在处理请求派分时，可以根据客户端提供的超媒体类型，选择对应的资源方法（Resource Method）。
- 在处理请求派分时，可以根据客户端提供的 Http 方法，选择对应的资源方法。
- 资源方法可以返回 Java 对象，由 Runtime 自行推断正确的返回状态。资源方法可以不明确指定返回的超媒体类型，由 Runtime 自行推断。
- 比如资源方法标注了 Produces，那么就使用标注提供的超媒体类型等。
- 可通过扩展点 MessageBodyWriter 处理不同类型的返回内容。当资源方法抛出异常时，根据异常影响 Http 请求。
- 可通过扩展点 ExceptionMapper 处理不同类型的异常。资源方法可按照期望的类型，访问 Http 请求的内容。
- 可通过扩展点 MessageBodyReader 处理不同类型的请求内容。资源对象和资源方法可接受环境组件的注入。

......中间还有很多分解的东西

> 当采用伦敦学派时，会按照调用栈顺序从外而内地实现不同的组件。

因而，我们首先需要先实现的是 ResourceServlet。那么细化任务列表：

- ResourceServlet
    - 将请求派分给对应的资源（Resource），并根据返回的状态、超媒体类型、内容，响应 Http 请求
    - 使用 OutboundResponse 的 status 作为 Http Response 的状态；
    - 使用 OutboundResponse 的 headers 作为 Http Response 的 Http Headers；
    - 通过 MessageBodyWriter 将 OutboundResponse 的 GenericEntity 写回为 Body；
    - 如果找不到对应的 MessageBodyWriter，则返回 500 族错误
    - 当资源方法抛出异常时，根据异常影响 Http 请求
    - 如果抛出 WebApplicationException，且 response 不为 null，则使用 response 响应 Http
    - 如果抛出 WebApplicationException，而 response 为 null，则通过异常的具体类型查找 ExceptionMapper，生产 response 响应 Http 请求
    - 如果抛出的不是 WebApplicationException，则通过异常的具体类型查找 ExceptionMapper，生产 response 响应 Http 请求

## 实现ResourceServletTest

添加测试类

- 添加测试类public class ResourceServletTest extends ServletTest{}
- 实现getServlet()方法，return new ResourceServlet()


添加todo

```java
// TODO: 2022/6/19 use status code as http status
// TODO: 2022/6/19 use headers as http headers
// TODO: 2022/6/19 writer body using MessageBodyWriter
// TODO: 2022/6/19 500 if MessageBodyWriter not found
// TODO: 2022/6/19 throw WebApplicationException with response, use response
// TODO: 2022/6/19 throw WebApplicationException with null response, use ExceptionMapper build response
// TODO: 2022/6/19 throw other exception, use ExceptionMapper build response
```

测试stub准备

- 添加private Runtime runtime field，然后在getServlet方法里面，runtime = Mockito.mock(Runtime.class);
- 把runtime传递给ResourceServlet作参数，然后创建构造函数，并且把runtime生成field（用重构手法）
- 添加private ResourceRouter router, ResourceContext resourceContext两个变量
- 在getServlet里面，Mockito.mock(ResourceRouter.class)赋值给router，Mockito.mock(ResourceContext.class)赋值给resourceContext
- 添加when返回值，when(runtime.getResourceRouter()).thenReturn(router);
- when(runtime.createResourceContext(any(), any())).thenReturn(resourceContext);

### use status code as http status的情况

#### 添加测试should_use_status_from_response

测试步骤：

- Mockito.mock(OutboundResponse.class)赋值给response，把OutboundResponse给mock掉
- when(response.getStatus()).thenReturn(Response.Status.NOT_MODIFIED.getStatusCode())选择一个300段的错误，不会因为什么都没有做，也返回200，不会因为代码出现错误，返回500。不是一个默认行为，能驱动把代码写出来
- When(router.dispatch(any(), eq(resourceContext))).thenReturn(response)
- get("/hello/world")赋值给HttpResponse(我在之前的代码里面写的就是这个地址，视频里面用的是/test)
- 断言Response.Status.NOT_MODIFIED.getStatusCode()和httpResponse.statusCode()相等，跑测试不通过

实现步骤：

- 在ResourceServlet添加一个override的service方法
- runtime.getResourceRouter()赋值给router
- router.dispatch(req, runtime.createResourceContext(req, resp))赋值给response
- resp.setStatus(response.getStatus())

### Use headers as http headers的情况

#### 添加测试should_use_http_headers_from_response()

测试步骤：

- Mockito.mock(OutboundResponse.class)赋值给response，把OutboundResponse给mock掉
- new MultivaluedHashmap<>();赋值给MultivaluedMap<String, Object> headers
- headers.addAll("Set-Cookie", new NewCookie.Builder("SESSION_ID").value("session").build(), new NewCookie.Builder("USER_ID").value("user").build())
- when(response.getHeaders()).thenReturn(headers);
- When(router.dispatch(any(), eq(resourceContext))).thenReturn(response)
- get("/hello/world")赋值给HttpResponse
- httpResponse.headers().allValues("Set-Cookie").toArray(String[]::new)...
- 把上面new NewCookie("SESSION_ID")提取成一个变量叫sessionId，另一个提取成userId
- 断言new Stirng[]{sessionId.toString(), useId.toString()}跟上面...allValues("Set-Cookie")那句相等

发现存在扩展点：

- sesionId.toString()是弃用的，需要用其它的方法来获取
- 有一种方式是在resources目录下创建文件，还有一种是setInstance，选择了后者
- 在测试的第3句，添加Mockito.mock(RuntimeDelegate.class)赋值给delegate，然后RuntimeDelegate.setInstance(delegate);
- When(delegate.createHeaderDelegate(eq(newCookie.class))).thenReturn(new RuntimeDelegate.HeaderDelegate<>(){})
- 并且overload了两个方法，一个是fromString，一个是toString，在toString里面，返回value.getName() + "=" + value.getValue()
- 把之前断言里面，改成new String[]{"SESSION_ID=session", "USER_ID=user"}

实现步骤：

- services方法里面，response.getHeaders()赋值给headers
- for(String name: headers.keySet())再for(Object value: headers.get(name))
- 然后RuntimeDelegate.getInstance().createHeaderDelegate(value.getClass())赋值给headerDelegate，去掉泛型
- resp.addHeader(name, headerDelegate.toString(value))，跑测试不通过，
- 把sessionId 和userId放到when下面去，跑测试还是不通过
- 在headers.addAll(...)下面添加when(response.getStatus()).thenReturn(Response.Status.NOT_MODIFIED.getStatusCode())跑测试就通过了
- 跑全部的测试，之前的测试没有通过
- 在前面的测试添加when(response.getHeaders()).thenReturn(new MultivaluedHashMap<>())




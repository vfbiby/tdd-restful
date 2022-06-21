# 42 | RESTFul Web Services (6): 如何处理JAX-RS定义的异常类？
待办列表：

ResourceServlet

- 将请求派分给对应的资源（Resource），并根据返回的状态、超媒体类型、内容，响应 Http 请求
    - ~~使用 OutboundResponse 的 status 作为 Http Response 的状态；~~
    - ~~使用 OutboundResponse 的 headers 作为 Http Response 的 Http Headers；~~
    - ~~通过 MessageBodyWriter 将 OutboundResponse 的 GenericEntity 写回为 Body；~~
    - 如果找不到对应的 MessageBodyWriter，则返回 500 族错误
- 当资源方法抛出异常时，根据异常影响 Http 请求
    - 如果抛出 WebApplicationException，且 response 不为 null，则使用 response 响应 Http
    - 如果抛出 WebApplicationException，而 response 为 null，则通过异常的具体类型查找 ExceptionMapper，生产 response 响应 Http 请求
    - 如果抛出的不是 WebApplicationException，则通过异常的具体类型查找 ExceptionMapper，生产 response 响应 Http 请求

## WebApplicationException的处理

### throw WebApplicationException with Response情况

#### 添加测试should_use_response_from_web_application_exception

##### 修改数据准备方法

- 复制一个OutboundResponseBuilder里面的build方法，参数改成Consumer<OutboundResponse> consumer
- 把when(router.dispatch(...))...这部分，变成consumer.accept(response)
- 把之前那个build方法里面的，全部删除，换成build(response -> when(router.dispatch(any(), eq(resourceContext))).thenReturn(response))跑测试是通过的，把这个build方法改成returnFrom
- 回到测试里面，把之前的build变量改名成response
- 在returnFrom下面添加void throwFrom(ResourceRouter router){}
- 复制returnFrom里面的实现，然后在when(router.dispatch(..))...前面加WebApplication exception = new WebApplicationException(response);把thenReturn改成thenThrow(exception)

##### 测试步骤：

- Response.status(Response.Status.FORBIDDEN).throwFrom(router)
- get("/hello/world")赋值给httpResponse
- assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode())
- 跑测试是失败的，主要是statusInfo没有stub掉

##### 实现步骤：

- 在build方法里面，when(response.getStatusInfo()).thenReturn(status)
- 在ResourceServlet的service方法里面，把response的定义和赋值分开
- try 赋值部分，然后catch WebApplicationException exception，然后exception.getResponse()赋值给response，并且强制类型转换

##### 补充测试内容：

- 在response链式调用上添加.headers(HttpHeaders.SET_COOKIE, new NewCookie.Builder("SESSION_ID").value("session").build())
- 再添加.entity(new GenericEntity<>("error", String.class), new Annotation[0])
- 断言new String[]{"SESSION_ID=session"}和httpResponse.headers().allValues(HttpHeaders.SET_COOKIE).toArray(String[]::new)相等
- 断言"error"和httpResponse.body()相等

### throw WebApplicationException with null response情况

#### 添加测试should_build_response_by_exception_mapper_if_null_response_from_web_application_exception

##### 测试步骤：

- 复制前面测试response那个链式调用，把entity调用的第一个参数改成null，因为之前已经测过了，所以去掉这个即可，header调用也不需要，直接删除
- get("/hello/world")赋值给httpResponse
- assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode())
- 发现测试的情况，不是entity为空的，但是这是一种sad path，所以再添加一个//TODO entity is null, ignore MessageBodyWriter
- 删除response那个链式调用，new WebApplicationException("error", (Response)null)赋值给exception
- when(router.dispatch(any(), eq(resourceContext))).thenThrow(exception)
- When(providers.getExceptionMapper(eq(WebApplicationException.class))).thenReturn(new ExceptionMapper<WebApplicationException>(){..})重载掉toResponse方法
- 把build方法里面，创建response，不包括consumer.accept(response)那部分提取成一个方法build
- 在toResponse里面，return response.status(Response.Status.FORBIDDEN).build()
- 走到这里的时候，发现了jax-rs规范不一致的地方，说是可以response为null，但是却直接返回了ServerError()，所以这个测试就不用测试了，忽略这个测试
- 把下面那个todo复制过来

### throw other exception, use ExceptionMapper build response的情况

##### 修改测试

- 把这个todo移到前面测试的上面
- 把WebApplicationException那一句，删除掉
- when(router.dispatch(...))...thenThrow(RuntimeException.class)
- 把这里面的泛型也都改成RuntimeException的，When(providers.getExceptionMapper...).thenReturn(new ExceptionMapper<RuntimeException>...)，以及override的地方，eq的地方等，然后用lambda替换一下
- 跑测试不通过

##### 实现步骤：

- 在service里面，catch(Throwable throwable)
- providers.getExceptionMapper(throwable.getClass())赋值给ExceptionMapper mapper
- mapper.toResponse(throwable)强制转换成(OutboundResponse)再赋值给response
- 跑测试失败，writer为空
- 把之前build(Consumer<OutboundResponse> consumer)里面的when(providers.getMessageBodyWriter...)...这一句，复制到build()里面，然后提取方法stubMessageBodyWriter
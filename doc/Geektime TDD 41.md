# 41 | RESTFul Web Services (5): 如何通过对数据测试的管理来凸显意图？

待办列表：

ResourceServlet

- 将请求派分给对应的资源（Resource），并根据返回的状态、超媒体类型、内容，响应 Http 请求
    - ~~使用 OutboundResponse 的 status 作为 Http Response 的状态；~~
    - ~~使用 OutboundResponse 的 headers 作为 Http Response 的 Http Headers；~~
    - 通过 MessageBodyWriter 将 OutboundResponse 的 GenericEntity 写回为 Body；
    - 如果找不到对应的 MessageBodyWriter，则返回 500 族错误
- 当资源方法抛出异常时，根据异常影响 Http 请求
    - 如果抛出 WebApplicationException，且 response 不为 null，则使用 response 响应 Http
    - 如果抛出 WebApplicationException，而 response 为 null，则通过异常的具体类型查找 ExceptionMapper，生产 response 响应 Http 请求
    - 如果抛出的不是 WebApplicationException，则通过异常的具体类型查找 ExceptionMapper，生产 response 响应 Http 请求

## 对测试进行简单的重组

前面在做第二个测试时，影响到了第一个测试，这是一种不好的信号，我们应该把它们重构成一个整体。

### 把构建response的提取出来

- 把response创建并且mock返回的代码放到一起，提取Response.Status.NOT_MODIFIED.getStatusCode()作为变量status

    - ```java
  Response.Status status = Response.Status.NOT_MODIFIED;
  OutboundResponse response = mock(OutboundResponse.class);
  when(response.getStatus()).thenReturn(status.getStatusCode());
  when(response.getHeaders()).thenReturn(headers);
  when(router.dispatch(any(), eq(resourceContext))).thenReturn(response);
    ```

    - 把下面的4句提取成方法response，并且idea会提示是不是替换全部类似的语句，这里我在第一次操作的时候，没有提示，发现如果两个地方的代码顺序不一致，是不会提示的，改成一致顺序就会提示。再把提取方法的参数顺丰改变一下，status在前面

    - 在调用处把status inline进来

should_use_status_from_response里面的一些重组

- 把第里面的sessionId和usrId也inline进来
- 把前面RuntimeDelegate创建和mock部分的代码，放到一个新增的@BeforeEach public void before里面，因为这是一个全局的，跟任何测试没有关系，只有当你使用了一个其它的cookie值的时候，才会需要修改

## 继续完成todo里面的测试项

### writer body using MessageBodyWriter的情况

- 把response方法移到最下面

#### 添加测试should_write_entity_to_http_response_using_message_body

测试步骤：

- 先在ResourceServletTest类下面加一个private Providers providers field
- 在getServlet里面，mock(Providers.class)赋值给providers，并且when(runtime.getProviders()).thenReturn(providers)
- 在测试里面new GenericEntity<>("entity", String.class)赋值给GenericEntity<Object> entity
- Annotation[] annotations = new Annotation[0];
- MediaType.TEXT_PLAIN_TYPE赋值给MediaType mediaType
- 调用response(Response.Status.OK, new MultivaluedHashMap<>(), entity, annotations, mediaType)
- 然后修改这个方法的签名，添加上这些参数，给entity添加一个默认值new GenericEntity<>("entity", String.class)。给annotations添加默认值new Annotation[0]。mediaType添加的默认值MediaType.TEXT_PLAIN_TYPE
- 在response方法里面，分别把这些stub掉，when(response.getGenericEntity()).thenReturn(entity)，when(response.getAnnotations()).thenReturn(annotations)，when(response.getMediaType()).thenReturn(mediaType)
- 在测试里面，继续when(providers.getMessageBodyWriter(eq(String.class), eq(String.class), eq(annotations), eq(mediaType))).thenReturn(new MessageBodyWriter<>(){...})重载掉isWriteable和writeTo两个方法
- 在writeTo方法里面，new PrintWriter(entityStream)赋值给writer，writer.write(s);writer.flush();
- 然后get("/hello/world")赋值给httpResponse，断言"entity"跟httpResponse.body()是相等的

实现步骤：

- 在ResourceServlet的service方法里面，for循环后面，response.getGenericEntity()赋值给entity
- 在赋值router下面，runtime.getProviders()赋值给providers
- 在最下面，providers.getMessageBodyWriter(entity.getRawType(), entity.getType(), response.getAnnotations(), response.getMediaType())赋值给writer
- writer.writeTo(entity.getEntity(), entity.getRawType(), entity.getType(), response.getAnnotations(), response.getMediaType(), response.getHeaders(), resp.getOutputStream())跑测试是通过的，但是跑全部测试should_use_status_from_response是失败的
- 把这个测试里面,when(providers.getMessageBodyWriter...)这一段，复制到失败的测试，把里面缺失的变量换成new Annotations[0], MediaType.TEXT_PLAIN_TYPE，然后又把这个换好的剪切到了before方法里面的

#### 重构测试

现在发现这三个测试，其实是很像的测试，但是却写了3遍，用了不一样的方法来进行校验。

##### 使用测试模板：

- 在测试类最下面添加class OutboundResponseBuilder{}
- 添加field
    - Response.Status.OK赋值给status
    - new MultivaluedHashMap()赋值给MultivaluedMap<String, Object> headers
    - new GenericEntity<>("entity", String.class)赋值给entity
    - new Annotation[0]赋值给annotations
    - MediaType.TEXT_PLAIN_TYPE赋值给mediaType
- 添加public OutboundResponseBuilder headers(String name, Object... values){headers.addAll(name, values);return this;}
- 添加public OutboundResponseBuilder status(Response.Status status){this.status = status;return this;}
- 添加public OutboundResponseBuilder entity(GenericEntity<Object> entity, Annotation[] annotatios){this.entity = entity;this.annotations = annotations;return this;}
- 添加void build(ResourceRouter router){}，然后把response方法里面的复制过来
- 把should_use_status_from_response里面调用response方法的改成new OutboundResponseBuilder().status(Response.Status.NOT_MODIFIED).build(router);
- 在should_use_http_headers_from_response里面，new OutboundResponseBuilder().headers("Set-Cookie", ...)把headers.addAll(...)里面的复制过来，再.build(router)，然后删除掉response方法，以前这里面准备的参数的变量
- 把new OutboundResponseBuilder()提取变量builder，复制到before里面，再introduce field
- 前面测试里面，new的地方，也可以改成builder.status(...)
- 在should_write_entity_to_http_response_using_message_body_writer里面，也用builder.entity(new GenericEntity<>("entity", String.class), new Annotation[0]).build(router);替换掉response方法调用，以及删除其它无用的变量
- 删除response方法，再跑测试，都是通过的
- 把OutboundResponseBuilder里面的的GenericEntity的泛型都改成String
- 把when(providers.getMesageBodyWriter(...)).thenReturn(...)这一部分也复制到builder里面来，然后把eq(new Annotation[0])换成same(annotations), eq(MediaType.TEXT_PLAIN_TYPE)改成eq(mediaType)
- 这样改了后，如果再回到ResourceServlet的service方法里面，把response.getAnnotations()换成new Annotation[0]，测试是会失败的

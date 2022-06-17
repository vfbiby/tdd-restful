# 37 | RESTful Web Services (1): 明确架构愿景与架构组件间的交互

## 拿架构愿景去做spike

我现在不知道怎么做，所以先刺探一下。用junit去做spike，它给我提供了很多便利。

### 添加ASpike刺探测试

- 添加测试类，ASpike
- 添加测试方法，should()
- 添加一具BeforeEach start方法
- 添加AfterEach stop
- 在ASpike添加一个Server server的field

在start方法

- 在start方法里面，new一个Server，端口是8080，赋值给server
- new一个ServerConnnector(server)赋值给connector
- server.addConnector(connector)
- new一个ServletContextHandler(server, "/")赋值给handler
- server.setHandler(handler)
- server.start()，添加一个抛出异常
- 再给handler添加一个Servlet，handler.addServlet(new ServletHolder(new HttpServlet(), "/"))，并且实现doGet方法，这里在new HttpServlet时，一定要选pathSpec在后面那个方法，还有addServlet时，也要选择正确的，要不一直提示有问题
- 在doGet方法里面，resp.getWriter().write("test");resp.getWriter().flush();

在stop方法

- server.stop()

在should()测试里面

- HttpClient.newHttpClient()赋值给client
- HttpRequest.newBuilder(new URI("http://localhost:8080")).GET().build();赋值给request
- client.send(request, HttpResponse.BodyHandlers.ofString())赋值给response
- 可以直接写成soutv(response.body())，也可以直接assertEquals("test", response.body())，甚至可以直接把soutv放到上面doGet实现的代码里面去也行

用自己最小的方法实现

- 在上面添加一个@Path("/test") static class TestResource{}
- 里面有一个@GET public String get(){}方法，直接返回"test"
- 在doGet里面，new TestResource().get()赋值给result，把下面write("test")的地方，直接换成write(result)
- 添加一个static class TestApplication extends Application{}
- 覆盖getClasses，并且在里面return Set.of(TestResource.class)
- 在上面再添加一个static class ResourceServlet extends HttpServlet{}
- 覆盖一下doGet方法，保持默认的super.doGet(req, resp)返回
- 添加一个private Application application，并且添加构造函数，赋值这个application
- 把前面的goGet方法里面的几行代码复制到这个doGet方法里面
- 在前面handler.addServlet里面的代码换成new ServletHolder里面，new ResourceServlet(new TestApplication())

ResourceServlet的doGet里面的尝试

- 首先是application.getClasses().stream().filter(c -> c.isAnnotationPresent(Path.class))赋值给rootResource
- 添加一个Object dispatch(HttpServletRequest req, Stream<Class<?>> rootResources){return null;}
- 在doGet里面，dispatch(req, rootResources)赋值给result
- write(result.toString())
- 在dispatch里面，rootResources.findfirst().get();赋值给rootClass
- rootClass.getConstructor().newInstance();赋值给rootResource，处理抛出异常的问题，添加一个try catch，抛出RuntimeException(e)
- rootClass.getMethods() filter m -> m.isAnnotationPresent(GET.class).findFirst().get()赋值给method
- return method.invoke(rootResource)，跑测试，发现是500错误了
- 给TestResource定义一下他默认的构造函数

实现BodyWriter

- 添加static class StringMessageBodyWriter implements MessageBodyWriter<String>{}
- 重载一下isWriteable和writeTo两个方法
- 在isWriteable里面，返回type==String.class
- 在writeTo里面，new PrintWriter(entityStream)赋值给writer，writer.write(s);writer.flush();

实现一个Providers

- 在上面添加一个static class TestProviders implements Providers{}
- 把4个方法都重载出来，但是其它方法不关心，只关心getMessageBodyWriter
- 给TestApplication的getClasses方法里面，构建Set时，添加一个StringMessageBodyWriter.class
- 给TestProviders添加一个private Application application的field，并且添加构造函数，然后在里面，this.application.getClasses() filter(c->MessageBodyWriter.class.isAssignableFrom(c)).map(c -> c.getConstructor().newInstance()).toList()
- 然后添加try catch 只后区Exception e然后抛出RuntimeException
- 把这一串赋值给List<MessageBodyWriter> writers，强制类型转换一下，再introduce field一下到TestProviders
- 在getMessageBodyWriter里面，writers filter(w -> w.isWraiteable(type, genericType, annotations, mediaType)).findFirst().get()

把TestProvider和ResourceServlet融合

- 在ResourceServlet里面，添加一个private Providers providers
- 添加一个新的构造函数，把两个field都初始化
- 在start里面，new ResourceServlet()时，把new Application提取变量application，再添加一个参数，new TestProviders(application)
- 删除另一个只有一个field的构造函数
- 在ResourceServlet的doGet里面，providers.getMessageBodyWriter(result.getClass(), null, null, null)赋值给MessageBodyWriter<Object> writer，并且做强制类型转换
- 然后writer.writeToo(result, null, null, null, null, null, resp.getOutputStream())
- 把之前另两句writer删除掉，再跑一下测试，有失败
- 给StringMessageBodyWriter添加一个默认构造函数，再跑测试是通过的
- 讲了一些spike时，尽量选择的原则
- 并且在goGet方法里面时，有可能是把现在的所有的实现，都try catch(Exception e)然后再providers.getExceptionMapper(e.getClass()).toResponse();}}



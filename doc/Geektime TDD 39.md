# 39 | RESTFul Web Services (3): 明确架构愿景与调用栈顺序

我们做spike，并不是要把所有的东西都细化，而是用一个足够小的结构，来理解我们最终的结构是怎么样的。

## TestResource的结构

最后我们会通过反射，调到TestResource的实例，最终实现的结果会被写回到http response里面去，那么它会有status code media type， headers， body等

添加示例效果

- 在get方法上面，添加@Produces(MediaType.TEXT_PLAIN)来指定mediaType
- status code，在执行过程中，如果没有其它的异常，那就是200
- headers需要额外的方法来指定
- body就是执行的返回值

如何指定headers呢

- 添加一个@GET @Path("/with-headers") public Response withHeaders(){}
- return Response.ok().header("Set-Cookie", new NewCookie.Builder("SESSION_ID").value("SID").build()).entity("string", new Annotation[0]).build();

如何指定body

- 添加@GET @Path("/generic") public GenericEntity<List<String>> generic(){}
- return new GenericEntity<>(List.of("abc", "def")){}，这样就可以在writeTo的时候，才能根据List<Person>或者List<Order>来确定怎么样转化成json
- 或者也可以@GET @Path("/pojo-generic") public List<String> pojoGeneric(){return Listof("abc", "def");}通过反射的方式去获取，这个时候就得在dispatch上根据类型信息去处理，或者在response里面，给一个.entity()更仔细的构建
- 还有就是void类型，通常是在put post时，@PUT public void update(){}，如果不是put，而是get时，void会返回204，no content，如果是post，会返回201，put时也有可能返回204

## OutboundResponse设计

- 从使用api的角度讲，我们在dispatch里面，除了做实际的派发之外，返回值的处理也是很重要的，在dispatch里面，把method.invoke(rootResource)提取变量result，//pojo Response, void, GenericEntity会有这样一些分类，需要我们来处理写回http response，这4个里面，只有Response里面，有code, header, mediaType, body齐全的，其它的几种类型都需要补全的。最好的方式，就是让dispatch统一返回Response，把dispatch方法的返回值改成Response，我们只需要在里面做一下封装就可以了，所以return Response.ok(result).build();但是这个是一个抽象方法，也需要自己提供实现，所以return new Response(){}，重载全部的方法，但是在getEntity方法里面，return result;
- 在doGet里面，dispatch的返回值类型改成Response
- result.getEntity();赋值给Object entity
- 把下面之前对result的引用，改成entity即可
- 把下面getMessageBodyWriter()的第四个参数改成result.getMediaType()
- Jax-rs在设计时，Response是包含客户端和服务端的，所以上面方法第三个annotation取不到，如果想获取，必须自己去子类化Response
- 在dispatch里面，new GenericEntity(result, method.getGenericReturnType())赋值给Generic entity，然后让new Response(){}里面的getEntity返回这个entity，后面我们所有的值就可以拿了
- 把前面doGet里面的entity的类型改成GenericEntity并且做强制类型转换
- 然后调用getMessageBodyWriter(entity.getRawType(), entity.getType(), ...)现在就剩下annotation没有处理
- 在dispatch下面添加一个static abstract class OutboundResponse extends Response{}
- 添加一个abstract GenericEntity getGenericEntity();
- 添加一个abstract Annotation[] getAnnotations();
- 把dispatch方法的返回值，变成OutboundResponse，return new OutboundResponse(){...}，并且implements两个抽象方法，
- 在getGenericEntity()方法里面，返回entity，默认的getAnnotation会return new Annotation[0];
- 把doGet方法里面result的类型也改成OutboundResponse，把下面entity用result.getGenericEntity()来赋值
- getMessageBodyWriter里面的第三个参数也可以用result.getAnnotations()来填充
- 下面的writer.writeTo(..., entity.getRawType(), entity.getType(), result.getAnnotations(), result.getMediaType(), result.getHeaders(), ...)

明显的坏味道

- 在goGet方法里面，把所有的值取出来，然后又放到writeTo里面去，所以我们直接在OutboundResponse里面添加一个abstract void write(HttpServletResponse response, Providers providers)
- 这样的话，上面的getGenericEntity和getAnnotation方法就不需要了，给它注释掉，只需要实现这个新写的方法即可
- 在goGet里面，赋值entity上面，result.write(resp, providers)，然后下面的赋值writer和writeTo都可以放到这个方法里面去，但是这一步，暂时并没有做，还是把之前注释的两个方法放出来，把这个write去掉了
- 在OutboundResponse下面添加一个interface ResourceRouter{}，里面有一个OutboundResponse dispatch(HttpServletRequest request, ResourceContext resourceContext)，这样我们的接口就明确了

## 如何处理spike的代码

整理spike的代码

- 在生产代码里面，建立一个public class ResourceServlet extends HttpServlet{}类
- 把OutboundResponse先move up level，再移到生产代码中
- 把ResourceRouter也移到生产代码去

包装TestApplication

- 在生产代码区添加一个public interface Runtime{}类
- 添加一个Providers getProviders();
- 添加一个ResourceContext createResourceContext(HttpServletRequest request, HttpServletResponse response);
- 添加一个Context getApplicationContext();
- 添加一个ResourceRouter getResourceRouter();

整理ASpike里面的代码

- 删除ResourceServlet类
- 删除TestApplication类
- 删除TestProviders类
- 删除TestResource类
- 删除StringMessageBodyWriter类
- 就只留下了start(),stop(),和should()三个测试的结构
- 把field server变成private
- 把ASpike变成abstract，添加一个protected abstract Servlet getServlet();
- 把start()里面改成new ServletHolder(getServlet())
- 把ASpike改名成ServletTest
- 添加一个protected URI path(String path) throws Exception{}
- return new URL(new URL("http://localhost:8080/"), path).toURI();
- 把之前的should()改成protected HttpResponse<String> get(String path) throws Exception{}
- 去掉里面的断言语句，把里面网址部分换成参数path，返回client.send(...)作为结果


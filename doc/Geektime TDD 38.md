# 38 | RESTFul Web Services (2): 根据Spike的结果，要如何调整架构愿景？

## 另一个Spike

上一轮没有考虑任何jxr-ac，这一次是看di容器怎么使用

TestProviders应该是Singleton的，无论什么进来，messageBody也不会重建。

Request Scope，即dispatch那部分，应该都创建新的实例，而不能使用之前的实例。这样就能使用一些局部状态，或者其它的一些方法。

### 处理MessageBodyWriter这部分

先让容器接管MessageBodyWriter的创建

- 在TestProviders的构造方法里面，new ContextConfig()赋值给config
- 把之前赋值writers的map之前的部分，提取变量writerClasses，并且在原来使用的地方，改成writerClasses.stream()...
- 然后for循环这个writerClasses，再config.component(writerClass, writerClass)
- Config.getContext()赋值给context
- writerClasses再map一下c -> context.get(ComponentRef.of(c)).get()).toList()赋值给writers，再加上类型转换，把之前的writers删除，再跑测试，还是通过的

尝试添加一个没有的依赖

- 在StringMessageBodyWriter里面，添加一个@Inject @Named("prefix") String prefix;的依赖，这时再跑测试，就会提示没有这个依赖
- 在TestApplication里面，添加一个public Config getConfig(){}方法，返回new Config(){@Named("prefix") public String name = "prefix";};
- 在TestProviders的构造函数里面，把参数类型改成TestApplication application，把field也改成TestApplication类型的，然后在里面，config.from(application.getConfig())，跑测试是出错的，是access权限问题，临时在ContextConfig里面，Optional<Object> value()里面，try部分，添加field.setAccessible(true)，再跑测试就通过了
- 在StringMessageBodyWriter的writeTo里面，添加writer.write(prefix)，跑测试发现是不通过的，修改测试的断言为prefixtest，再跑测试又通过了。

如法在ResourceServlet里面炮制一个

- 把field 和构造函数的application的类型都改成TestApplication
- 在构造函数里，new ContextConfig()赋值给config，config.from(application.getConfig())
- 把doGet里面，获取rootResources的，复制过来，并且toList
- for循环这个rootResources，然后config.component(rootResource, rootResource)
- config.getContext()赋值给context，并且introduce field
- 把dispatch里面，用context.get(ComponentRef.of(rootClass)).get()来获取rootResource，再跑测试，还是正常的

在TestResource里面，也可以用inject来注入了

- 添加一个@Inject @Named("prefix") String prefix;的field，跑测试是通过的
- 在get方法里面，返回prefix + "test"，再跑测试就失败了，变成了prefixprefixtest
- 修改断言的地方为上面的值

#### 容器生成有大量重复部分

- 在ResourceServlet和TestProviders里面，容器生成部分有大量重复的

把容器生成放到TestApplication里面

- 在TestApplication里面，添加一个public Context getContext(){return null;}
- 添加一个构造函数，里面是new ContextConfig()赋值给config，然后config.from(getConfig())
- 把TestProviders的构造函数里面，生成writerClasses和for循环部分复制过来，修改一下错误的变量引用
- 把ResourceServlet里面，生成rootResources和for循环的部分也复制过来，同样修改一下变量引用
- config.getContext()赋值给context，再introduce field
- 然后让getContext里面，返回这个context
- 把前面两处复制代码过来的地方，删除相关的代码，然后context = application.getContext()即可，在writerClasses这里需要保留，其它不需要的直接删除即可

### Jax-rs的其它注入

主要介绍了很多jax-rs的一些注入方式，

- 在TestApplication里面，添加一个public ResourceContext createResourceContext(HttpServletRequest request, HttpServletResponse response){return null;}
- 在ResourceServlet的doGet方法里面，创建ResourceContext rc;并且赋值为application.createResourceContext(req, resp)
- 把rc放在dispatch的第三个参数，传过去，并且使用重构方法，让dispatch添加这个参数
- 在dispatch里面，在赋值rootResource时，用rc.initResource(...)包裹起来
- 在application的createResourceContext里面，先给一个空实现new ResourceContext，重载getResource和initResource两个方法，在initResource里面，返回resource自己
- 在TestResource里面，添加一个@QueryParam("q") String q;



































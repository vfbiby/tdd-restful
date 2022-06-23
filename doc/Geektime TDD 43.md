# 43 | RESTFul Web Services (7): 剩下两个Sad Path场景该如何处理？

待办列表：

ResourceServlet

- 将请求派分给对应的资源（Resource），并根据返回的状态、超媒体类型、内容，响应 Http 请求
    - ~~使用 OutboundResponse 的 status 作为 Http Response 的状态；~~
    - ~~使用 OutboundResponse 的 headers 作为 Http Response 的 Http Headers；~~
    - ~~通过 MessageBodyWriter 将 OutboundResponse 的 GenericEntity 写回为 Body；~~
    - 如果找不到对应的 MessageBodyWriter，则返回 500 族错误
    - 如果entity为空，则忽略body
- 当资源方法抛出异常时，根据异常影响 Http 请求
    - ~~如果抛出 WebApplicationException，且 response 不为 null，则使用 response 响应 Http~~
    - ~~如果抛出的不是 WebApplicationException，则通过异常的具体类型查找 ExceptionMapper，生产 response 响应 Http 请求~~

去掉了一个不用测的情况，添加了一个新的sad path情况

## 添加其它Sad Path的情况

添加了几种异常情况

```java
// TODO: 2022/6/22 500 if header delegate 
// TODO: 2022/6/22 500 if exception mapper

// TODO: 2022/6/22 exception mapper 
// TODO: 2022/6/22 providers gets exception mapper
// TODO: 2022/6/22 runtime delegate
// TODO: 2022/6/22 header delegate
// TODO: 2022/6/22 providers gets message body writer
// TODO: 2022/6/22 message body writer write
```

## 继续处理Sad Path

### entity is null, ignore messageBodyWriter的情况

把这个todo移到todo的最上面，开始写

#### 添加测试should_not_call_message_body_writer_if_entity_is_null

测试步骤：

- 复制should_write_entity_to_http_response_using_message_body_writer里面的代码过来
- 把里面的new GenericEntity...改成null，response.entity(null)
- 断言Response.Status.OK.getStatusCode()跟httpResponse.statusCode是相等的
- 断言""跟resopnse.body()相等

实现步骤：

- 在ResourceServlet里面，赋值entity后面，判断entity != null，就执行writer的赋值和writer.write两句

### exception mapper的情况

#### 添加测试should_use_response_from_web_application_exception_thrown_by_exception_mapper

测试步骤：

- 复制should_build_response_by_exception_mapper_if_null_response_from_web_application_exception过来
- when(providers.getExceptionMapper...).thenReturn时改成thenReturn(exception -> {throw new WebApplicationException(response.status(Response.Status.FORBIDDEN).build());})

实现步骤：

- 在service方法里面catch(Throwable throwable)里面的实现，再try一次然后catch(WebApplicationException exception){response = (OutboundResponse) exception.getResponse();}

### 递归的情况展示

##### 添加测试

- 通过复制上面的测试，改名成should_map_exception_thrown_by_exception_mapper
- 在throw的时候，改成IllegalArgumentException()
- 当providers.getExceptionMapper(eq(IllegalArgumentException.class))时，应该返回exception-> response.status(Response.Status.FORBIDDEN).build()

##### 实现步骤：

- 在Throwable throwable里面的catch WebApplicationException exception后面，再加一层catch Throwable throwable1
- 然后复制之前被try包裹起来的mapper和response过来，但是用的变量是这个throwable1，然后跑测试是通过的。

#### 改成递归的情况

##### 先提取方法

- 把下面resp.setStatus..., headers, for(...), GenericEntity entity..., if(entity != null)...这部分抽成一个方法respond
- 把providers抽成一个全局field，然后把赋值放到构造函数里面去，因为它是singleton的
- 把respond方法的providers参数删除，调用处的也删除，可以手动，也可以用重构的方式
- 再跑测试，是通过的
- 把有response赋值的地方，全部用这个新提取的方法来替换respond(resp, router.dispatch(req, runtime.createResourceContext(req, resp)))这样的形式（在前面加respond(resp, ...），一共有5处需要替换
- 然后把最下面，之前提取方法的调用respond(resp, response)删除掉，把那个无用的response变量也删除

##### 尝试改成递归

- 添加一个private void respond_(HttpServletResponse resp, OutboundResponse response){}
- try{}catch(WebApplicationException exception){respond(resp, (OutboundResponse)exception.getResponse());}catch(Throwable throwable){}
- 需要延迟来抛出异常，最简单的延迟处理，就是使用Supplier<OutboundResponse> supplier
- try{respond(resp, supplier.get())}，然后catch(WebApp...){respond_(resp, ()-> (OutboundResponse)exception.getResponse()))}
- catch(Throwable throwable){respond_(resp, () -> {ExceptionMapper mapper = providers.getExceptionMapper(throwable.getClass()); return (OutboundResponse) mapper.toResponse(throwable);})}，再把这里面的抽成一个方法from(throwable)，改成lambda方式
- 把service里面的try catch换成respond_(resp, () -> router.dispatch(req, runtime.createResourceContext(req, resp)));跑测试全部通过
- 把respond_重命名成respond
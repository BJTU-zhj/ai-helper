# ai-helper

Microservices structure:

- `ai-code-assistant-service`: existing LangChain4j code assistant service, default port `8081`
- `ai-super-host`: Spring AI based general agent host service skeleton, default port `8082`
- `ai-gateway`: gateway service skeleton for forwarding traffic to internal AI services, default port `8080`

## Build all modules

```bash
./mvnw clean package
```

## Run services

```bash
./mvnw -pl ai-code-assistant-service spring-boot:run
./mvnw -pl ai-super-host spring-boot:run
./mvnw -pl ai-gateway spring-boot:run
```

1、使用自定义advisor
2、使用结构化输出
3、持久化存储
4、promprtTemplate的使用，可以返回prompt、message对象

rag
文档读取和切割
1、文档加载器，本地知识库 最终转化为document--------documentReader接口

存储
云知识库
本地文件
数据库

检索


增强检索生成的顾问主要了解两个
主要是两个顾问
一个是questionAnswerAdvisor,简单，就是拼接
一个是RetrivalAugmentionAdvisor,自定义----支持查询转换器，检索器

2、ETL，从读取到存储，

Reader->Transformer->Wirter

Reader: documentReader接口，返回document对象

Transformer：大致可分为3类，一个是文本分割TextSplitter,一个是元数据增强转化器，可以用ai生成标签，摘要，还有一个是结构化没用的少

Reader:可以存文件系统，也可以存向量数据库，VectorStore接口存向量数据库，可以将document对象存进数据库，且提供构建搜索请求的功能

测试pgsql安装向量插件，可以用云

3、检索，spring ai将这部分分为：检索前，检索时和检索后三个环节，提供了大量的顾问支持
在预检索阶段，系统接收用户的原始查询，通过查询转换和查询扩展等方法对其进行优化，输出增强的用户查询。
rewriteQueryAdvisor,查询重写，调用大模型，给你规范的查询
translationQueryAdvisor,查询翻译，调用大模型，给你翻译的查询
compressionQueryAdvisor,查询压缩，调用大模型，将会话历史和当前提问，给你压缩的查询
MultiQueryRewriteAdvisor,多查询重写，调用大模型，将原有查询变换成多个查询

在检索阶段，系统使用增强的查询从知识库中搜索相关文档，可能涉及多个检索源的合并，最终输出一组相关文档。
documentRetriever,不同检索源的检索器，可以设置检索条件，元数据条件检索等
concatentionDocumentJoiner,文档连接，将多个检索结果（可以不同数据源），连接成一个文档，去重

在检索后阶段，系统对检索到的文档进行进一步处理，包括排序、选择最相关的子集以及压缩文档内容，输出经过优化的相关文档集。

4、增强生成阶段
主要的几个顾问
一个是questionAnswerAdvisor,简单，就是拼接
一个是RetrivalAugmentionAdvisor,自定义----支持查询转换器，检索器（上边检索的检索前和检索后的检索器1）
一个是contextualQueryAdvisor,空上下文处理，没找到相关知识，也让回答
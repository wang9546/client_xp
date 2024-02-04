package org.prompt;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.zhipu.oapi.ClientV4;
import com.zhipu.oapi.Constants;
import com.zhipu.oapi.service.v4.model.*;
import io.reactivex.Flowable;
import org.prompt.client.ClientCar;
import org.prompt.client.ClientReq;
import org.prompt.event.MyEventListener;
import org.prompt.event.MyEventListenerImpl;
import org.prompt.event.MyEventSource;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


public class V4OkHttpClientTest {

    private static final String API_KEY = "4a56df81ff2a33c197a3d6e79bf0652d";

    private static final String API_SECRET = "rnGAk47KBsGKltq9";

    private static final ClientV4 client = new ClientV4.Builder(API_KEY,API_SECRET).build();

    private static final ObjectMapper mapper = defaultObjectMapper();


    public static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        mapper.addMixIn(ChatFunction.class, ChatFunctionMixIn.class);
        mapper.addMixIn(ChatCompletionRequest.class, ChatCompletionRequestMixIn.class);
        mapper.addMixIn(ChatFunctionCall.class, ChatFunctionCallMixIn.class);
        return mapper;
    }

    // 请自定义自己的业务id
    private static final String requestIdTemplate = "a991ea53-3e65-463d-b50d-534e63073cff";

    /**
     * sse调用
     */
    private static void testSseInvoke() {
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), "第一章 财务管理制度\n" +
                "第二节 财务组织体系\n" +
                "第三条 是什么");
        messages.add(chatMessage);
        String requestId = String.format(requestIdTemplate, System.currentTimeMillis());
        // 函数调用参数构建部分
        List<ChatTool> chatToolList = new ArrayList<>();
        ChatTool chatTool = new ChatTool();
        chatTool.setType(ChatToolType.RETRIEVAL.value());
        Retrieval retrieval = new Retrieval();
        retrieval.setKnowledge_id("1752939537920942080");
        retrieval.setPrompt_template("请求模型时的知识库模板，默认模板：\n" +
                "从文档\n" +
                "\"\"\"\n" +
                "{{knowledge}}\n" +
                "\"\"\"\n" +
                "中找问题\n" +
                "\"\"\"\n" +
                "{{question}}\n" +
                "\"\"\"\n" +
                "的答案，找到答案就仅使用文档语句回答问题，找不到答案就用自身知识回答并且告诉用户该信息不是来自文档。\n" +
                "不要复述问题，直接开始回答\n" +
                "\n");
        chatTool.setRetrieval(retrieval);

        chatToolList.add(chatTool);

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(Constants.ModelChatGLM4)
                .stream(Boolean.TRUE)
                .messages(messages)
                .requestId(requestId)
                .tools(chatToolList)
                .toolChoice("auto")
                .build();
        ModelApiResponse sseModelApiResp = client.invokeModelApi(chatCompletionRequest);
        if (sseModelApiResp.isSuccess()) {
            AtomicBoolean isFirst = new AtomicBoolean(true);
            ChatMessageAccumulator chatMessageAccumulator = mapStreamToAccumulator(sseModelApiResp.getFlowable())
                    .doOnNext(accumulator -> {
                        {
                            if (isFirst.getAndSet(false)) {
                                System.out.print("Response: ");
                            }
                            if (accumulator.getDelta() != null && accumulator.getDelta().getTool_calls() != null) {
                                String jsonString = mapper.writeValueAsString(accumulator.getDelta().getTool_calls());
                                System.out.println("tool_calls: " + jsonString);
                            }
                            if (accumulator.getDelta() != null && accumulator.getDelta().getContent() != null) {
                                System.out.print(accumulator.getDelta().getContent());
                            }
                        }
                    })
                    .doOnComplete(System.out::println)
                    .lastElement()
                    .blockingGet();

            Choice choice = new Choice(chatMessageAccumulator.getChoice().getFinishReason(), 0L, chatMessageAccumulator.getDelta());
            List<Choice> choices = new ArrayList<>();
            choices.add(choice);
            ModelData data = new ModelData();
            data.setChoices(choices);
            data.setUsage(chatMessageAccumulator.getUsage());
            data.setId(chatMessageAccumulator.getId());
            data.setCreated(chatMessageAccumulator.getCreated());
            data.setRequestId(chatCompletionRequest.getRequestId());
            sseModelApiResp.setFlowable(null);
            sseModelApiResp.setData(data);
        }
        System.out.println("model output:" + JSON.toJSONString(sseModelApiResp));
    }

    public static Flowable<ChatMessageAccumulator> mapStreamToAccumulator(Flowable<ModelData> flowable) {
        return flowable.map(chunk -> {
            return new ChatMessageAccumulator(chunk.getChoices().get(0).getDelta(), null, chunk.getChoices().get(0), chunk.getUsage(), chunk.getCreated(), chunk.getId());
        });
    }

    public static void main(String[] args) throws InterruptedException, UnsupportedEncodingException {
//        testSseInvoke();


        String requestId = String.format(requestIdTemplate, System.currentTimeMillis());


        ClientReq req = new ClientReq();
        req.setApiKey("4a56df81ff2a33c197a3d6e79bf0652d");
        req.setApiSecret("rnGAk47KBsGKltq9");
        req.setQuery("离职还要缴纳社保？");
        req.setKnowledge_id("1752939537920942080");
        req.setKnowledge_prompt_template(
                "你需要只输出： 抱歉，找不到答案。 不要携带任何其他任何文字");
        req.setPrompt_template("用户问题\n" +
                "{{question}}\n" +
                "\n" +
                "你叫“小p”，是小鹏汽车的智能助手，请回答用户问题，不要解释，不要说明，不要引导。\n" +
                "\n" +
                "这些词都是指代小鹏汽车：小鹏、小鹏汽车、小P、G3、G3i、G6、G9、P7、P5、P7i、X9、P6\n" +
                "\n" +
                "车型介绍\n" +
                "G9：价格263,900起，轴距2998，后备箱容积660 / 1576（后排座椅放倒），前舱储物盒容积71。\n" +
                "X9：价格359,800起，轴距3160，电池能量84.5，液冷恒温无热蔓延磷酸铁锂电池包（IP68级防尘防水）\n" +
                "\n" +
                "输出格式\n" +
                "{\n" +
                "\"简短回答\":\"xx\",\n" +
                "\"对简短回答的展开描述\":\"xx\",\n" +
                "\"用户可能想继续问的问题1\":\"xx\",\n" +
                "\"用户可能想继续问的问题2\":\"xx\"\n" +
                "}");
        req.setModel(Constants.ModelChatGLM4);
        req.setRequestId(requestId);
        MyEventSource eventSource = new MyEventSource();
        // 可以根据业务实现 MyEventListenerImpl
        MyEventListener listener = new MyEventListenerImpl();
        eventSource.addEventListener(listener);

        req.setSseListener(eventSource);

        ClientCar client1 = new ClientCar();
        client1.sseInvoke(req);
    }

}
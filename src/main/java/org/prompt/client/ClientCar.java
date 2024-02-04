package org.prompt.client;

import com.alibaba.fastjson.JSON;
//import com.zhipu.oapi.ClientV4;
//import com.zhipu.oapi.service.v4.model.*;
import com.zhipu.oapi.Constants;
import com.zhipu.oapi.service.v4.model.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.io.*;
import java.util.*;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ClientCar {
    private volatile boolean flag = false;
    private volatile boolean knowledge_flag = false;
    private volatile boolean knowledge_finish = false;
    public void setFlag(boolean flag) {
        this.flag = flag;
    }
    public boolean getFlag() {
        return flag;
    }
    public void setFinish(boolean knowledge_finish) {
        this.knowledge_finish = knowledge_finish;
    }
    public boolean getFinish() {
        return knowledge_finish;
    }
    public void setKnowledgeFlag(boolean knowledge_flag) {
        this.knowledge_flag = knowledge_flag;
    }
    public boolean getKnowledgeFlag() {
        return knowledge_flag;
    }
    public String replacePrompt(String question,String prompt){
        return prompt.replace("{{question}}", question);
    }

    public static String generateToken(String id, String secret, int expSeconds) {
        long currentTimeMillis = System.currentTimeMillis();

        System.out.println(currentTimeMillis);
        Map<String, Object> payload = new HashMap<>();
        payload.put("api_key", id);
        payload.put("exp", currentTimeMillis + expSeconds * 1000L);
        payload.put("timestamp", currentTimeMillis);

        return Jwts.builder()
                .setClaims(payload)
                .signWith(SignatureAlgorithm.HS256, secret.getBytes())
                .setHeaderParam("alg", "HS256")
                .setHeaderParam("sign_type", "SIGN")
                .setHeaderParam("typ", "JWT")
                .compact();
    }

    public ModelApiResp sseInvoke(ClientReq request) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        executorService.submit(new WorkerTaskKnowledge(request));
        executorService.submit(new WorkerTaskSearch(request));
        executorService.shutdown();
        return null;
    }

    private EventResp createEventResp(ClientReq request,StringBuilder respText) {
        EventResp.ChoicesData choicesData = new EventResp.ChoicesData();
        choicesData.setIndex(0);
        EventResp.DeltaData deltaData = new EventResp.DeltaData("assistant",respText.toString());
        List<EventResp.DeltaData> deltaDataList = new ArrayList<>();
        deltaDataList.add(deltaData);
        choicesData.setDelta(deltaDataList);
        EventResp resp = new EventResp();
        resp.setId(request.getRequestId());
        resp.setModel(request.getModel());
        List<EventResp.ChoicesData> choicesDataList = new ArrayList<>();
        choicesDataList.add(choicesData);
        resp.setChoices(choicesDataList);
        return resp;
    }
    private EventResp createEventRespFinish(ClientReq request) {
        EventResp.ChoicesData choicesData = new EventResp.ChoicesData();
        choicesData.setIndex(0);
        choicesData.setFinish_reason("stop");;
        EventResp.DeltaData deltaData = new EventResp.DeltaData("assistant","");
        List<EventResp.DeltaData> deltaDataList = new ArrayList<>();
        deltaDataList.add(deltaData);
        choicesData.setDelta(deltaDataList);
        EventResp resp = new EventResp();
        resp.setId(request.getRequestId());
        resp.setModel(request.getModel());
        List<EventResp.ChoicesData> choicesDataList = new ArrayList<>();
        resp.setChoices(choicesDataList);
        return resp;
    }
    // 定义一个Runnable任务
    class WorkerTaskKnowledge implements Runnable {
        private final ClientReq request;

        public WorkerTaskKnowledge(ClientReq request) {
            this.request = request;
        }
        @Override
        public void run() {

            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost httpPost = getHttpPost(request,false); // 替换为你的SSE接口地址
            StringBuilder respText = new StringBuilder();

            try {
                HttpResponse response = httpClient.execute(httpPost);
                // 检查响应码
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    throw new RuntimeException("Failed : HTTP error code : " + statusCode);
                }
                // 获取响应体并转换为BufferedReader
                BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
                String output;
                // 逐行读取SSE事件
                while ((output = br.readLine()) != null) {
                    // SSE事件通常以 "data: " 开头，你可以根据实际情况处理事件
                    if (output.startsWith("data: ")) {
                        try{
                            EventResp m = JSON.parseObject(output.substring(6), EventResp.class);
                            // 处理事件数据
                            if(Objects.equals(m.getChoices().get(0).getFinish_reason(), "stop")) {
                                if (respText.toString().startsWith("抱歉，找不到答案")){
                                    setFlag(true);
                                }else {
                                    System.out.println("基于知识库的正常输出");
                                    setKnowledgeFlag(true);

                                    // 正常输出，在这里返回
                                    EventResp resp = createEventResp(request,respText);
                                    request.getSseListener().someOperationThatGeneratesEvents(resp);
                                    request.getSseListener().someOperationThatGeneratesEvents(m);
                                }
                                break;
                            };
                            respText.append(m.getChoices().get(0).getDelta().get(0).getContent());
                        }catch (Exception e){
                            String s = output.substring(6);
                        }
                    }
                }
                EntityUtils.consume(response.getEntity());

            } catch (Exception e) {
                System.out.println("发送请求出现异常！" + e);
            }finally {
                setFinish(true);
                // 关闭HttpClient
                closeHttpClient(httpClient);
            }
        }
    }

    // 定义一个Runnable任务
    class WorkerTaskSearch implements Runnable {
        private final ClientReq request;

        public WorkerTaskSearch(ClientReq request) {
            this.request = request;
        }
        @Override
        public void run() {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost httpPost = getHttpPost(request,true); // 替换为你的SSE接口地址
            StringBuilder respText = new StringBuilder();
            boolean respFlag = true;
            try {
                HttpResponse response = httpClient.execute(httpPost);
                // 检查响应码
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    throw new RuntimeException("Failed1 : HTTP error code : " + statusCode);
                }
                // 获取响应体并转换为BufferedReader
                BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
                String output;
                // 逐行读取SSE事件
                while ((output = br.readLine()) != null) {
                    // SSE事件通常以 "data: " 开头，你可以根据实际情况处理事件
                    if (output.startsWith("data: ")) {
                        try{
                            if (getFinish()&&getKnowledgeFlag()){
                                respFlag = false;
                                // 已经处理完成了，不需要等待了
                                break;
                            }
                            if (getFlag()&&respFlag){
                                // 知识库的出现错误，需要使用搜索的返回

                                EventResp resp = createEventResp(request,respText);
                                request.getSseListener().someOperationThatGeneratesEvents(resp);
                                // 累积的返回一次后不再返回
                                respFlag = false;
                            }
                            EventResp m = JSON.parseObject(output.substring(6), EventResp.class);
                            if (!respFlag){
                                // 累积的返回

                                request.getSseListener().someOperationThatGeneratesEvents(m);
                            }
                            // 处理事件数据
                            if(Objects.equals(m.getChoices().get(0).getFinish_reason(), "stop")) {
                                break;
                            };
                            respText.append(m.getChoices().get(0).getDelta().get(0).getContent());

                        }catch (Exception e){
                            String s = output.substring(6);
                        }
                    }
                }
                // 释放资源
                EntityUtils.consume(response.getEntity());
            } catch (Exception e) {
                System.out.println("发送请求出现异常！" + e);
            }finally {
                // 关闭HttpClient
                try{
                    closeHttpClient(httpClient);
                }catch (Exception e){
                    System.out.println("关闭HttpClient出现异常！" + e);
                }
            }
            long time = TimeUnit.SECONDS.toMillis(20);
            long start = System.currentTimeMillis();
            while (true){
                if (time < 0) {
                    System.out.println("请求超时");
                    break;
                }
                if (!respFlag){
                    // 发过了
                    break;
                }
                if (getKnowledgeFlag()){
                    // 发过了
                    break;
                }
                if (getFinish()){
                    // 知识库的完成了
                    // 没有发消息
                    EventResp resp = createEventResp(request,respText);
                    request.getSseListener().someOperationThatGeneratesEvents(resp);
                    EventResp finishResp = createEventRespFinish(request);
                    request.getSseListener().someOperationThatGeneratesEvents(finishResp);
                    break;
                }else {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                long current = System.currentTimeMillis() - start;
                start = System.currentTimeMillis();
                time -= current;
            }
        }
    }


    private  HttpPost getHttpPost(ClientReq request, Boolean search) {
        String token = generateToken(request.getApiKey(),request.getApiSecret(),864000);
        System.out.println(token);
        String sseUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
        HttpPost httpPost = new HttpPost(sseUrl); // 替换为你的SSE接口地址
        // 设置请求头部信息，这里设置Content-Type为application/json
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Accept", "text/event-stream");
        httpPost.setHeader("Authorization",   token);
        StringEntity entity = getRequest(request,search);
        httpPost.setEntity(entity);
        return httpPost;
    }

    public  StringEntity getRequest(ClientReq request, Boolean search) {
        ChatCompletionRequest chatCompletionRequest=ChatCompletionRequest.builder()
                .model(request.getModel())
                .stream(Boolean.TRUE)
                .requestId(request.getRequestId())
                .toolChoice("auto")
                .build();
        List<ChatMessage> messages = new ArrayList<>();
        List<ChatTool> chatToolList = new ArrayList<>();
        ChatTool chatTool = new ChatTool();
        if (search){
            // 搜索
            ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), replacePrompt(request.getQuery(),request.getPrompt_template()));
            messages.add(chatMessage);
            chatTool.setType(ChatToolType.WEB_SEARCH.value());
            WebSearch webSearch = new WebSearch();
            webSearch.setEnable(Boolean.TRUE);
            chatTool.setWeb_search(webSearch);
            chatToolList.add(chatTool);
            chatCompletionRequest.setTemperature(0.9F);
            chatCompletionRequest.setTopP(0.8F);
        }else {
            // 知识库
            ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), request.getQuery());
            messages.add(chatMessage);
            chatTool.setType(ChatToolType.RETRIEVAL.value());
            Retrieval retrieval = new Retrieval();
            retrieval.setKnowledge_id(request.getKnowledge_id());
            retrieval.setPrompt_template(request.getKnowledge_prompt_template());
            chatTool.setRetrieval(retrieval);
            chatToolList.add(chatTool);
            chatCompletionRequest.setTemperature(0.1F);
            chatCompletionRequest.setTopP(0.1F);
        }
        chatCompletionRequest.setMessages(messages);
        chatCompletionRequest.setTools(chatToolList);
        return new StringEntity(JSON.toJSONString(chatCompletionRequest), "UTF-8");
    }

    private  void  closeHttpClient(CloseableHttpClient httpClient) {
        // 关闭HttpClient
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                System.out.println("发送请求出现异常！" + e);
            }
        }
    }
}

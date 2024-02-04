package org.prompt.client;

import lombok.Data;
import org.prompt.event.MyEventSource;

@Data
public class ClientReq {

    private String apiKey;
    private String apiSecret;

    /**
     * 所要调用的模型编码
     */
    private String model;

    /**
     * 由用户端传参，需保证唯一性；用于区分每次请求的唯一标识，用户端不传时平台会默认生成
     */
    private String requestId;

    private String query;
    private String knowledge_id;
    /**
     *请求模型时的知识库模板，默认模板： 从文档 """ {{ knowledge}} """ 中找问题 """ {{question}} """ 的答案，找到答案就仅使用文档语句回答问题，找不到答案就用自身知识回答并且告诉用户该信息不是来自文档。 不要复述问题，直接开始回答 注意：用户自定义模板时，知识库内容占位符 和用户侧问题占位符必是{{ knowledge}} 和{{question}}，其他模板内容用户可根据实际场景定义
     */

    private String knowledge_prompt_template;
    private String prompt_template;

    private MyEventSource sseListener;

}

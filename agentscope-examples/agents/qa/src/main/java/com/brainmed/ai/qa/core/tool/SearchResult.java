package com.brainmed.ai.qa.core.tool;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * 搜索结果结构化数据，包含正文和引用来源。
 *
 * <p>工具将此对象序列化为 JSON 返回给主模型，主模型据此在回答中使用 [序号] 标注引用来源。
 *
 * <p>{@code rawAnnotations} 承载 Responses API 返回的原始 annotation 节点，
 * 由各 Tool 子类按自己的 annotation 类型（url_citation / doc_citation）提取引用。
 */
@Getter
@Setter
public class SearchResult {

    /**
     * 搜索结果正文
     */
    private String text;

    /**
     * 引用来源列表（由 Tool 类从 rawAnnotations 中提取）
     */
    private List<Citation> citations;

    /**
     * Responses API 返回的原始 annotation 节点，供 Tool 类自行提取引用。
     */
    private List<JsonNode> rawAnnotations;

    public SearchResult() {
    }

    public SearchResult(String text, List<Citation> citations) {
        this.text = text;
        this.citations = citations;
    }

    public SearchResult(String text, List<Citation> citations, List<JsonNode> rawAnnotations) {
        this.text = text;
        this.citations = citations;
        this.rawAnnotations = rawAnnotations;
    }


    /**
     * 单条引用来源。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Citation {
        /**
         * 序号（从1开始）
         */
        private int index;
        /**
         * 页面标题
         */
        private String title;
        /**
         * 页面 URL
         */
        private String url;

        /**
         * 内容类型
         * - 知识库检索：info（资讯）、community_qa（社区问答）、literature（文献）、user（医生个人简介）、product（产品）、jbbk（疾病百科）、ai_meeting_agenda（会议日程摘要）
         * - 联网搜索：external（系统外部数据）
         * - 异常降级：unknown（解析失败或未知类型，无法跳转）
         */
        private String type = "external"; //联网搜索 出来的结果

        /**
         * 内容摘要/片段
         * - 联网搜索：网页摘要（summary）
         * - 知识库检索：内容片段（chunk_title）
         */
        private String content;

        /**
         * 网站名称（联网搜索时有值，如"搜索引擎-道客巴巴"）
         */
        private String siteName;

        /**
         * 网站Logo URL（联网搜索时可能有值）
         */
        private String logoUrl;

        /**
         * 发布时间（联网搜索时有值）
         */
        private String publishTime;

        /**
         * 扩展参数（知识库检索时有值，用于APP内原生跳转）
         */
        private JSONObject ext;

        /**
         * 来源类型：knowledge_base（知识库）或web（互联网）
         */
        private String source;

        public Citation(int index, String title, String url) {
            this.index = index;
            this.title = title;
            this.url = url;
        }
    }
}

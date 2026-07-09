package com.brainmed.ai.qa.core.tool;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 同义词词典：结合 jieba 分词 + 同义词库实现查询扩展。
 * 
 * <p>工作流程：
 * <ol>
 *   <li>启动时加载自定义词典（cut.txt）到 jieba，确保医学术语不被错误切分</li>
 *   <li>加载同义词文件（synonyms_v3.txt），构建双向映射</li>
 *   <li>查询时先用 jieba 分词，再对每个词查同义词扩展</li>
 * </ol>
 * 
 * <p>同义词文件格式：每行逗号分隔的同义词组，如 {@code GBM, gbm, 胶质母细胞瘤,胶母细胞瘤}
 */
@Component
public class SynonymDictionary {

    private static final Logger log = LoggerFactory.getLogger(SynonymDictionary.class);
    private static final String SYNONYMS_FILE = "words/synonyms_v3.txt";
    private static final String CUSTOM_DICT_FILE = "words/cut.txt";

    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    /**
     * 自定义词典（按长度降序，用于 jieba 分词后的二次匹配）
     */
    private List<String> customDict = new ArrayList<>();

    /**
     * 词 -> 同义词集合（包含自身）
     * 例如：gbm -> {GBM, gbm, 胶质母细胞瘤, 胶母细胞瘤}
     */
    private final Map<String, Set<String>> synonymMap = new HashMap<>();

    /**
     * 所有已知词汇（用于快速判断）
     */
    private final Set<String> allKnownWords = new HashSet<>();

    @PostConstruct
    public void init() {
        // 1. 加载自定义词典到 jieba
        loadCustomDictionary();
        // 2. 加载同义词库
        loadSynonyms();
        log.info("同义词词典初始化完成: 已知词 {} 个, 同义词组 {} 个", allKnownWords.size(), synonymMap.size());
    }

    /**
     * 加载自定义词典（cut.txt）。
     * jieba-analysis 1.0.2 不支持运行时加词，所以我们将自定义词
     * 存入 dictionary 列表，在分词后做二次匹配（最大正向匹配）。
     */
    private void loadCustomDictionary() {
        try {
            ClassPathResource resource = new ClassPathResource(CUSTOM_DICT_FILE);
            if (!resource.exists()) {
                log.warn("自定义词典文件不存在: {}", CUSTOM_DICT_FILE);
                return;
            }

            List<String> words = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        words.add(line);
                    }
                }
            }
            // 按长度降序排列，确保最大正向匹配优先匹配长词
            words.sort((a, b) -> b.length() - a.length());
            this.customDict = words;
            log.info("自定义词典加载完成: {} 个词", words.size());
        } catch (Exception e) {
            log.error("加载自定义词典失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 加载同义词文件，构建双向映射。
     */
    private void loadSynonyms() {
        try {
            ClassPathResource resource = new ClassPathResource(SYNONYMS_FILE);
            if (!resource.exists()) {
                log.warn("同义词文件不存在: {}", SYNONYMS_FILE);
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int lineNum = 0;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    String[] words = line.split(",");
                    Set<String> synonymGroup = new LinkedHashSet<>();
                    for (String word : words) {
                        String trimmed = word.trim();
                        if (!trimmed.isEmpty()) {
                            synonymGroup.add(trimmed);
                        }
                    }

                    if (synonymGroup.size() < 2) {
                        continue;
                    }

                    for (String word : synonymGroup) {
                        String lowerWord = word.toLowerCase();
                        allKnownWords.add(lowerWord);
                        synonymMap.put(lowerWord, synonymGroup);
                    }
                }
                log.debug("同义词文件加载完成，共 {} 行", lineNum);
            }
        } catch (Exception e) {
            log.error("加载同义词文件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取指定词的所有同义词（包括自身）。
     */
    public Set<String> getSynonyms(String word) {
        if (word == null || word.isBlank()) {
            return Collections.singleton(word);
        }
        String lowerWord = word.toLowerCase();
        Set<String> synonyms = synonymMap.get(lowerWord);
        return synonyms != null ? synonyms : Collections.singleton(word);
    }

    /**
     * 判断指定词是否在词典中（有同义词）。
     */
    public boolean contains(String word) {
        return word != null && allKnownWords.contains(word.toLowerCase());
    }

    /**
     * 使用 jieba 分词 + 自定义词典二次匹配。
     * 
     * <p>流程：
     * <ol>
     *   <li>先用 jieba 基础分词</li>
     *   <li>将分词结果拼接回原文，用自定义词典做最大正向匹配</li>
     *   <li>匹配到的词作为整体，未匹配的部分保留 jieba 分词结果</li>
     * </ol>
     */
    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        // 先用自定义词典做最大正向匹配
        List<String> tokens = new ArrayList<>();
        int i = 0;
        int len = text.length();

        while (i < len) {
            char c = text.charAt(i);

            // 跳过标点和空白
            if (Character.isWhitespace(c) || isPunctuation(c)) {
                i++;
                continue;
            }

            // 尝试匹配自定义词典（已按长度降序）
            boolean matched = false;
            for (String word : customDict) {
                int wlen = word.length();
                if (i + wlen <= len && text.substring(i, i + wlen).equalsIgnoreCase(word)) {
                    tokens.add(text.substring(i, i + wlen));
                    i += wlen;
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                // 自定义词典没匹配到，用 jieba 分词这个片段
                // 取连续的中文/英文字符作为一个片段
                int start = i;
                while (i < len && !Character.isWhitespace(text.charAt(i)) && !isPunctuation(text.charAt(i))) {
                    i++;
                }
                String fragment = text.substring(start, i);
                if (!fragment.isEmpty()) {
                    // 用 jieba 分词这个片段
                    List<SegToken> jiebaTokens = segmenter.process(fragment, JiebaSegmenter.SegMode.INDEX);
                    for (SegToken token : jiebaTokens) {
                        String w = token.word.trim();
                        if (!w.isEmpty()) {
                            tokens.add(w);
                        }
                    }
                }
            }
        }

        return tokens;
    }

    private static boolean isPunctuation(char c) {
        String punct = "\uFF0C\u3002\uFF01\uFF1F\u3001\uFF1B\uFF1A\u201C\u201D\u2018\u2019\uFF08\uFF09\u3010\u3011\u300A\u300B,.!?;:\"'()[]{}";
        return punct.indexOf(c) >= 0;
    }

    /**
     * 从文本中提取所有已知词汇（通过 jieba 分词）。
     */
    public List<String> extractKnownWords(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<String> tokens = tokenize(text);
        log.debug("jieba 分词结果: {} -> {}", text, tokens);

        return tokens.stream()
                .filter(token -> allKnownWords.contains(token.toLowerCase()))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 扩展查询：使用 jieba 分词，将已知词扩展为所有同义词。
     * 
     * <p>例如：
     * <ul>
     *   <li>"GBM是什么" → jieba ["GBM", "是", "什么"] → 匹配 "gbm" → "GBM是什么 胶质母细胞瘤 胶母细胞瘤"</li>
     *   <li>"胶质母细胞瘤的治疗" → jieba ["胶质母细胞瘤", "的", "治疗"] → "胶质母细胞瘤的治疗 GBM"</li>
     * </ul>
     */
    public String expandQuery(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }

        List<String> knownWords = extractKnownWords(query);
        if (knownWords.isEmpty()) {
            return query;
        }

        // 收集所有同义词
        Set<String> allSynonyms = new LinkedHashSet<>();
        for (String word : knownWords) {
            Set<String> synonyms = getSynonyms(word);
            allSynonyms.addAll(synonyms);
        }

        // 计算需要额外添加的同义词
        String lowerQuery = query.toLowerCase();
        List<String> extraTerms = new ArrayList<>();
        for (String synonym : allSynonyms) {
            if (!lowerQuery.contains(synonym.toLowerCase()) && synonym.length() >= 2) {
                extraTerms.add(synonym);
            }
        }

        if (extraTerms.isEmpty()) {
            return query;
        }

        String expanded = query + " " + String.join(" ", extraTerms);
        log.info("查询扩展: '{}' -> '{}'", query, expanded);
        return expanded;
    }
}

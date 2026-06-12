package io.agentscope.builder.saton.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.builder.saton.tools.props.ImageSearchProperties;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 图片搜索工具 —— 通过 Unsplash / Pixabay API 搜索配图
 *
 * <p>当 PPT 幻灯片需要配图时，用此工具根据 slide 主题搜索免费高质量图片。
 * 搜索到的图片 URL 可用于 slides.json 中的 element.image。
 *
 * <p>配置源（免费）：
 * <ul>
 *   <li>Unsplash: 注册 https://unsplash.com/developers → 获取 Access Key</li>
 *   <li>Pixabay: 注册 https://pixabay.com/api/ → 获取 API Key</li>
 * </ul>
 *
 * <p>不需要在回答里显示「提示：你需要注册 xxx」之类的话——用户已经知道。
 */
@Component
@EnableConfigurationProperties({ImageSearchProperties.class})
public class ImageSearchTool {

    private static final Logger log = LoggerFactory.getLogger(ImageSearchTool.class);

    private static final String UNSPLASH_ENDPOINT = "https://api.unsplash.com/search/photos";
    private static final String PIXABAY_ENDPOINT = "https://pixabay.com/api/";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ImageSearchProperties properties;

    public ImageSearchTool(ImageSearchProperties properties) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(
                        properties.getTimeoutSeconds() > 0 ? properties.getTimeoutSeconds() : 15))
                .build();
        this.objectMapper = new ObjectMapper();
        this.properties = properties;
    }

    @Tool(name = "search_image", description = "搜索配图。"
            + "当制作 PPT 需要为某页幻灯片配图时调用此工具。"
            + "根据幻灯片主题生成英文关键词进行搜索，返回图片 URL 列表。"
            + "支持搜索任意主题的照片和插画。")
    public String searchImage(
            @ToolParam(name = "query", description = "搜索关键词（建议用英文，效果更好）") String query,
            @ToolParam(name = "count", description = "需要的图片数量，默认 3，最多 8", required = false)
            Integer count) {

        int targetCount = (count != null && count > 0) ? Math.min(count, 8) : properties.getPerPage();
        String source = properties.getSource();

        return switch (source) {
            case "pixabay" -> searchPixabay(query, targetCount);
            default -> searchUnsplash(query, targetCount);
        };
    }

    /**
     * 搜索 Unsplash（高质量专业摄影图片），需配置 unsplash-access-key
     */
    private String searchUnsplash(String query, int count) {
        String apiKey = properties.getUnsplashAccessKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "错误：imagesearch.unsplash-access-key 未配置。";
        }

        try {
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String url = UNSPLASH_ENDPOINT + "?query=" + encodedQuery + "&per_page=" + count;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Client-ID " + apiKey)
                    .header("Accept-Version", "v1")
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .GET()
                    .build();

            log.debug("Searching Unsplash: query={}", query);
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Unsplash API error: status={}, body={}",
                        response.statusCode(), response.body());
                return "图片搜索失败，HTTP " + response.statusCode();
            }

            return formatUnsplashResults(response.body(), query);

        } catch (Exception e) {
            log.error("Image search failed", e);
            return "图片搜索出错: " + e.getMessage();
        }
    }

    /**
     * 搜索 Pixabay（CC0 公共领域图片），需配置 pixabay-api-key
     */
    private String searchPixabay(String query, int count) {
        String apiKey = properties.getPixabayApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "错误：imagesearch.pixabay-api-key 未配置。";
        }

        try {
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String url = PIXABAY_ENDPOINT + "?key=" + apiKey
                    + "&q=" + encodedQuery
                    + "&per_page=" + count
                    + "&safesearch=true";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .GET()
                    .build();

            log.debug("Searching Pixabay: query={}", query);
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Pixabay API error: status={}, body={}",
                        response.statusCode(), response.body());
                return "图片搜索失败，HTTP " + response.statusCode();
            }

            return formatPixabayResults(response.body(), query);

        } catch (Exception e) {
            log.error("Image search failed", e);
            return "图片搜索出错: " + e.getMessage();
        }
    }

    /**
     * 解析 Unsplash 返回的 JSON，格式化为易读文本
     */
    private String formatUnsplashResults(String json, String query) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.get("results");

        if (results == null || !results.isArray() || results.isEmpty()) {
            return "未找到 \"" + query + "\" 相关图片。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("图片搜索结果: ").append(query).append("\n\n");

        int index = 1;
        for (JsonNode item : results) {
            String imageUrl = item.path("urls").path("regular").asText();
            String thumbUrl = item.path("urls").path("small").asText();
            String description = item.path("alt_description").asText("");
            String photographer = item.path("user").path("name").asText("Unknown");
            String photographerUrl = item.path("user").path("links").path("html").asText("");
            String unsplashUrl = item.path("links").path("html").asText();

            if (description == null || description.isBlank()) {
                description = item.path("description").asText("");
            }

            sb.append("第").append(index).append("张: ").append(imageUrl).append("\n");
            if (!description.isBlank()) {
                sb.append("  描述: ").append(description).append("\n");
            }
            sb.append("  摄影: ").append(photographer).append(" on Unsplash\n");
            sb.append("  缩略图: ").append(thumbUrl).append("\n");
            if (index < results.size()) {
                sb.append("\n");
            }
            index++;
        }

        sb.append("\n使用说明：从上方图片中选择最贴合幻灯片主题的，"
                + "将其 URL 填入 slides.json 中对应 slide 的 element.image → options.path。"
                + "如果使用 Pixabay 则无需署名；如果使用 Unsplash，"
                + "在 PPT 末页添加一行 \"Photos by Unsplash\" 即可。");

        return sb.toString();
    }

    /**
     * 解析 Pixabay 返回的 JSON，格式化为易读文本
     */
    private String formatPixabayResults(String json, String query) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode hits = root.get("hits");

        if (hits == null || !hits.isArray() || hits.isEmpty()) {
            return "未找到 \"" + query + "\" 相关图片。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("图片搜索结果: ").append(query).append("\n\n");

        int index = 1;
        for (JsonNode item : hits) {
            String imageUrl = item.path("largeImageURL").asText();
            String previewUrl = item.path("previewURL").asText();
            String tags = item.path("tags").asText("");
            String photographer = item.path("user").asText("");
            int width = item.path("imageWidth").asInt();
            int height = item.path("imageHeight").asInt();

            sb.append("第").append(index).append("张: ").append(imageUrl).append("\n");
            sb.append("  标签: ").append(tags).append("\n");
            sb.append("  摄影: ").append(photographer).append(" (Pixabay, CC0)\n");
            sb.append("  尺寸: ").append(width).append("×").append(height).append("\n");
            sb.append("  预览图: ").append(previewUrl).append("\n");
            if (index < hits.size()) {
                sb.append("\n");
            }
            index++;
        }

        sb.append("\n使用说明：Pixabay 图片为 CC0 公共领域，无需署名，可直接使用。");

        return sb.toString();
    }
}
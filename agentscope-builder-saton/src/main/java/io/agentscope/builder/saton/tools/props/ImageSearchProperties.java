package io.agentscope.builder.saton.tools.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 图片搜索配置属性 —— 对应 application.yml 中 imagesearch.* 配置项
 *
 * <p>使用 Unsplash API（免费），注册获取 Access Key：
 * <a href="https://unsplash.com/developers">https://unsplash.com/developers</a>
 * （免费，无需信用卡，Demo 50 次/小时，可申请更高免费额度）
 *
 * <p>也可配置为 Pixabay API：
 * <a href="https://pixabay.com/api/">https://pixabay.com/api/</a>
 * （免费，5000 次/小时，图片 CC0 公共领域）
 */
@ConfigurationProperties(prefix = "imagesearch")
public class ImageSearchProperties {

    /** Unsplash Access Key */
    private String unsplashAccessKey = "";

    /** Pixabay API Key（可选备选） */
    private String pixabayApiKey = "";

    /** 搜索源：unsplash / pixabay */
    private String source = "unsplash";

    /** 请求超时秒数 */
    private int timeoutSeconds = 15;

    /** 每页返回图片数 */
    private int perPage = 5;

    public String getUnsplashAccessKey() {
        return unsplashAccessKey;
    }

    public void setUnsplashAccessKey(String unsplashAccessKey) {
        this.unsplashAccessKey = unsplashAccessKey;
    }

    public String getPixabayApiKey() {
        return pixabayApiKey;
    }

    public void setPixabayApiKey(String pixabayApiKey) {
        this.pixabayApiKey = pixabayApiKey;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getPerPage() {
        return perPage;
    }

    public void setPerPage(int perPage) {
        this.perPage = perPage;
    }
}
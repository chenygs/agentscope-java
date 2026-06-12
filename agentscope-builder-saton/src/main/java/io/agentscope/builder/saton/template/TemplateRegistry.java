package io.agentscope.builder.saton.template;

import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.template.dto.TemplateVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.util.*;

@Slf4j
@Component
public class TemplateRegistry {

    private static final String LOCATION = "classpath:templates/*/template.json";

    private final List<TemplateVO> templates = new ArrayList<>();

    @PostConstruct
    void loadTemplates() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(LOCATION);
            for (Resource r : resources) {
                try {
                    Map<String, Object> map = JsonUtil.mapper()
                            .readValue(r.getInputStream(), new TypeReference<Map<String, Object>>() {});
                    String id = (String) map.get("id");
                    String name = (String) map.get("name");
                    String description = (String) map.get("description");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> agent = (Map<String, Object>) map.get("agent");
                    if (id != null && name != null) {
                        templates.add(new TemplateVO(id, name, description != null ? description : "", agent));
                    }
                } catch (Exception e) {
                    log.warn("failed to load template: {}", r.getFilename(), e);
                }
            }
            templates.sort(Comparator.comparing(TemplateVO::id));
        } catch (Exception e) {
            log.warn("failed to scan templates", e);
        }
    }

    public List<TemplateVO> list() { return List.copyOf(templates); }

    public Optional<TemplateVO> get(String id) {
        return templates.stream().filter(t -> t.id().equals(id)).findFirst();
    }
}

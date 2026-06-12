package io.agentscope.builder.saton.template;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.template.dto.TemplateVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateRegistry registry;

    public TemplateController(TemplateRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<TemplateVO> list() {
        return registry.list();
    }

    @GetMapping("/{id}")
    public TemplateVO get(@PathVariable("id") String id) {
        return registry.get(id).orElseThrow(() -> new NotFoundException("template not found: " + id));
    }
}

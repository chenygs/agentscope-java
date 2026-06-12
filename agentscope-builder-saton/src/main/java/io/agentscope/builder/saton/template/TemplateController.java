package io.agentscope.builder.saton.template;

import io.agentscope.builder.saton.common.R;
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
    public R<List<TemplateVO>> list() {
        return R.okList(registry.list());
    }

    @GetMapping("/{id}")
    public R<TemplateVO> get(@PathVariable("id") String id) {
        return R.ok(registry.get(id).orElseThrow(() -> new NotFoundException("template not found: " + id)));
    }
}

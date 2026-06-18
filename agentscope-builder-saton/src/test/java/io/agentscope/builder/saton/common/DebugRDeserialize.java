package io.agentscope.builder.saton.common;

import io.agentscope.builder.saton.agent.orm.dto.AgentVO;
import io.agentscope.builder.saton.common.json.JsonUtil;
import tools.jackson.core.type.TypeReference;

/**
 * Quick debug main to verify Jackson 3 R&lt;AgentVO&gt; deserialization.
 */
public class DebugRDeserialize {
    public static void main(String[] args) throws Exception {
        // Test 1: Deserialize AgentVO directly
        String agentJson = "{\"id\":1,\"ownerId\":\"admin\",\"agentId\":\"test\",\"name\":\"T\","
                + "\"description\":\"d\",\"sysPrompt\":\"s\",\"agentType\":\"REACT\","
                + "\"defaultModelProviderId\":1,\"maxIters\":5,\"toolSpecs\":[],"
                + "\"skillRepositories\":[],\"middlewareSpecs\":[],\"subagentRefs\":[],"
                + "\"createdAt\":0,\"updatedAt\":0}";
        AgentVO vo = JsonUtil.mapper().readValue(agentJson, AgentVO.class);
        System.out.println("Direct AgentVO maxIters: " + vo.maxIters());

        // Test 2: Deserialize R<AgentVO> with TypeReference
        String rJson = "{\"code\":200,\"data\":" + agentJson + ",\"msg\":\"ok\"}";
        R<AgentVO> r = JsonUtil.mapper().readValue(rJson, new TypeReference<R<AgentVO>>() {});
        System.out.println("R<AgentVO> maxIters: " + r.data().maxIters());
        System.out.println("R<AgentVO> data type: " + r.data().getClass().getName());
        System.out.println("R<AgentVO> data.id: " + r.data().id());
        System.out.println("R<AgentVO> data.agentId: " + r.data().agentId());
    }
}

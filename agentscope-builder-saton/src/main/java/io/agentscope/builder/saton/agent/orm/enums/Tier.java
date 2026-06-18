package io.agentscope.builder.saton.agent.orm.enums;

/**
 * Access tier for agent sharing. Ordered by ascending privilege:
 * CLONE &lt; RUN &lt; EDIT.
 */
public enum Tier {
    CLONE, RUN, EDIT;

    public boolean atLeast(Tier min) {
        return compareTo(min) >= 0;
    }
}

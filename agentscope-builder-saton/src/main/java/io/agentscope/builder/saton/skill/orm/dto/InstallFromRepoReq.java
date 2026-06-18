package io.agentscope.builder.saton.skill.orm.dto;

public record InstallFromRepoReq(int repoIndex, String skillName, String targetName, Boolean overwrite) {}

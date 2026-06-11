package io.agentscope.builder.saton.skill.dto;

public record InstallFromRepoReq(int repoIndex, String skillName, String targetName, Boolean overwrite) {}

package io.agentscope.builder.saton.workspace.dto;

/** 一个文件 / 目录的简要视图。 */
public record FileNodeVO(String name, String path, String type, long size) { }

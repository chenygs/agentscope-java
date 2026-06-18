package io.agentscope.builder.saton.marketplace.service.impl;

import io.agentscope.builder.saton.marketplace.service.BuilderMarketplace;
import io.agentscope.builder.saton.marketplace.service.MarketSkillContent;
import io.agentscope.builder.saton.marketplace.service.MarketSkillSummary;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Nacos-backed marketplace. All Nacos SDK classes are accessed via reflection so the project can
 * run without the optional nacos-client dependency.
 *
 * <p>Uses the maintainer client (not the regular AiService client, which only exposes downloads)
 * to drive the paged {@code listSkills} API and pull SKILL.md via
 * {@code getSkillVersionDetail(..., "LATEST")}.
 *
 * <p>Missing dependency surfaces as {@link IllegalStateException} at construction time.
 */
@Slf4j
public class NacosBuilderMarketplace implements BuilderMarketplace {

    // Fully-qualified Nacos class names (never loaded at compile time)
    private static final String CLASS_AI_MAINTAINER_FACTORY =
            "com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory";
    private static final String CLASS_SKILL_MAINTAINER_SERVICE =
            "com.alibaba.nacos.maintainer.client.ai.SkillMaintainerService";
    private static final String CLASS_PROPERTY_KEY_CONST =
            "com.alibaba.nacos.api.PropertyKeyConst";
    private static final String CLASS_PAGE =
            "com.alibaba.nacos.api.model.Page";
    private static final String CLASS_SKILL_SUMMARY =
            "com.alibaba.nacos.api.ai.model.skills.SkillSummary";
    private static final String CLASS_SKILL =
            "com.alibaba.nacos.api.ai.model.skills.Skill";
    private static final String CLASS_SKILL_RESOURCE =
            "com.alibaba.nacos.api.ai.model.skills.SkillResource";
    private static final String CLASS_NACOS_EXCEPTION =
            "com.alibaba.nacos.api.exception.NacosException";

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 50;
    private static final String LATEST_VERSION = "LATEST";

    private final String id;
    private final String serverAddr;
    private final String namespaceId;
    private final String username;
    private final String accessKey;
    private final Object service; // AiMaintainerService instance (reflection)

    public NacosBuilderMarketplace(
            String id,
            String serverAddr,
            String namespaceId,
            String username,
            String password,
            String accessKey,
            String secretKey) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (serverAddr == null || serverAddr.isBlank()) {
            throw new IllegalArgumentException("serverAddr must not be blank");
        }
        this.id = id;
        this.serverAddr = serverAddr.trim();
        this.namespaceId = (namespaceId == null || namespaceId.isBlank()) ? "public" : namespaceId;
        this.username = blankToNull(username);
        this.accessKey = blankToNull(accessKey);

        Properties props = new Properties();
        try {
            Class<?> propKeyConst = Class.forName(CLASS_PROPERTY_KEY_CONST);
            props.setProperty(
                    (String) propKeyConst.getField("SERVER_ADDR").get(null), this.serverAddr);
            props.setProperty(
                    (String) propKeyConst.getField("NAMESPACE").get(null), this.namespaceId);
            if (this.username != null) {
                props.setProperty(
                        (String) propKeyConst.getField("USERNAME").get(null), this.username);
                if (password != null) {
                    props.setProperty(
                            (String) propKeyConst.getField("PASSWORD").get(null), password);
                }
            }
            if (this.accessKey != null) {
                props.setProperty(
                        (String) propKeyConst.getField("ACCESS_KEY").get(null), this.accessKey);
                if (secretKey != null) {
                    props.setProperty(
                            (String) propKeyConst.getField("SECRET_KEY").get(null), secretKey);
                }
            }

            Class<?> factoryClass = Class.forName(CLASS_AI_MAINTAINER_FACTORY);
            Method createMethod = factoryClass.getMethod("createAiMaintainerService", Properties.class);
            this.service = createMethod.invoke(null, props);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "nacos skill repository support not installed (add nacos-client dependency)", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause != null && cause.getClass().getName().equals(CLASS_NACOS_EXCEPTION)) {
                throw new IllegalStateException(
                        "Failed to create Nacos AiMaintainerService for marketplace "
                                + id + " (" + this.serverAddr + ")", cause);
            }
            throw new IllegalStateException(
                    "Failed to create Nacos AiMaintainerService for marketplace "
                            + id + " (" + this.serverAddr + ")", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to initialize NacosBuilderMarketplace: " + e.getMessage(), e);
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String type() {
        return "nacos";
    }

    @Override
    public String displayLocation() {
        return serverAddr + " / ns=" + namespaceId;
    }

    @Override
    public List<MarketSkillSummary> list() {
        try {
            Method skillMethod = service.getClass().getMethod("skill");
            Object skillService = skillMethod.invoke(service);
            if (skillService == null) return List.of();

            Class<?> pageClass = Class.forName(CLASS_PAGE);
            Class<?> summaryClass = Class.forName(CLASS_SKILL_SUMMARY);
            Class<?> skillServiceClass = Class.forName(CLASS_SKILL_MAINTAINER_SERVICE);

            Method listSkillsMethod = skillServiceClass.getMethod(
                    "listSkills", String.class, String.class, String.class, int.class, int.class);
            Method getPageItems = pageClass.getMethod("getPageItems");
            Method getPagesAvailable = pageClass.getMethod("getPagesAvailable");
            Method getName = summaryClass.getMethod("getName");
            Method getDescription = summaryClass.getMethod("getDescription");
            Method getEditingVersion = summaryClass.getMethod("getEditingVersion");
            Method getReviewingVersion = summaryClass.getMethod("getReviewingVersion");

            List<MarketSkillSummary> all = new ArrayList<>();
            int pageNo = 1;

            while (pageNo <= MAX_PAGES) {
                Object page = listSkillsMethod.invoke(
                        skillService, namespaceId, null, null, pageNo, PAGE_SIZE);
                if (page == null) {
                    break;
                }

                List<?> items = (List<?>) getPageItems.invoke(page);
                if (items == null || items.isEmpty()) {
                    break;
                }

                for (Object s : items) {
                    String skillName = (String) getName.invoke(s);
                    String desc = (String) getDescription.invoke(s);
                    String editingVersion = (String) getEditingVersion.invoke(s);
                    String reviewingVersion = (String) getReviewingVersion.invoke(s);
                    String version = editingVersion != null ? editingVersion : reviewingVersion;
                    all.add(new MarketSkillSummary(skillName, desc, version));
                }

                int pagesAvailable = (int) getPagesAvailable.invoke(page);
                if (pageNo >= pagesAvailable || items.size() < PAGE_SIZE) {
                    break;
                }
                pageNo++;
            }

            return all;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause != null && cause.getClass().getName().equals(CLASS_NACOS_EXCEPTION)) {
                throw new IllegalStateException(
                        "Nacos listSkills failed for " + serverAddr + "/" + namespaceId, cause);
            }
            throw new IllegalStateException(
                    "Nacos listSkills failed for " + serverAddr + "/" + namespaceId, e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Nacos listSkills failed for " + serverAddr + "/" + namespaceId, e);
        }
    }

    @Override
    public MarketSkillContent fetch(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            Method skillMethod = service.getClass().getMethod("skill");
            Object skillService = skillMethod.invoke(service);

            Class<?> skillServiceClass = Class.forName(CLASS_SKILL_MAINTAINER_SERVICE);
            Method getSkillVersionDetail = skillServiceClass.getMethod(
                    "getSkillVersionDetail", String.class, String.class, String.class);
            Object skill = getSkillVersionDetail.invoke(
                    skillService, namespaceId, name.trim(), LATEST_VERSION);
            if (skill == null) {
                return null;
            }

            Class<?> skillClass = Class.forName(CLASS_SKILL);
            Method getSkillName = skillClass.getMethod("getName");
            Method getSkillDesc = skillClass.getMethod("getDescription");
            Method getSkillMd = skillClass.getMethod("getSkillMd");
            Method getResource = skillClass.getMethod("getResource");

            String skillName = (String) getSkillName.invoke(skill);
            String desc = (String) getSkillDesc.invoke(skill);
            String markdown = (String) getSkillMd.invoke(skill);
            if (markdown == null || markdown.isEmpty()) {
                return null;
            }

            Object upstream = getResource.invoke(skill);
            Map<String, Object> resourcesMap;
            if (upstream instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) upstream;
                resourcesMap = m;
            } else {
                resourcesMap = new java.util.LinkedHashMap<>();
            }
            Map<String, String> resources = new LinkedHashMap<>();
            if (resourcesMap != null && !resourcesMap.isEmpty()) {
                Class<?> resourceClass = Class.forName(CLASS_SKILL_RESOURCE);
                Method getContent = resourceClass.getMethod("getContent");
                Method getResName = resourceClass.getMethod("getName");
                for (Map.Entry<String, Object> entry : resourcesMap.entrySet()) {
                    Object value = entry.getValue();
                    if (value == null) {
                        continue;
                    }
                    Object content = getContent.invoke(value);
                    if (content == null) {
                        continue;
                    }
                    String resName = (String) getResName.invoke(value);
                    String path = (resName != null && !resName.isBlank()) ? resName : entry.getKey();
                    resources.put(path, (String) content);
                }
            }

            return new MarketSkillContent(skillName, desc, markdown, resources);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause != null && cause.getClass().getName().equals(CLASS_NACOS_EXCEPTION)) {
                throw new IllegalStateException(
                        "Nacos getSkillVersionDetail failed for "
                                + serverAddr + "/" + namespaceId + "/" + name, cause);
            }
            throw new IllegalStateException(
                    "Nacos getSkillVersionDetail failed for "
                            + serverAddr + "/" + namespaceId + "/" + name, e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Nacos getSkillVersionDetail failed for "
                            + serverAddr + "/" + namespaceId + "/" + name, e);
        }
    }

    @Override
    public void close() {
        // AiMaintainerService does not expose a close(); the underlying HTTP client is reaped
        // when this instance is GC'd. Nothing else to release.
        log.debug("Closing nacos marketplace {} ({})", id, serverAddr);
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}

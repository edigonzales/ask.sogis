package ch.so.agi.ask.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import ch.so.agi.ask.model.PlannerOutput;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Spring-basierte Implementierung des {@link ToolRegistry}, die @McpTool-
 * Beans findet, für den MCP-Client registriert und deren Rückgaben in das im
 * README beschriebene PlannerResult-Schema (Status, Items, Message) überführt.
 */
@Component
public class SpringMcpToolRegistry implements ToolRegistry, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(SpringMcpToolRegistry.class);

    private final Map<String, RegisteredTool> tools = new HashMap<>();
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        discoverTools();
    }

    private void discoverTools() {
        log.info("Discovering @McpTool methods...");
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> type = bean.getClass();

            // CGLIB-Proxies: auf die User-Klasse gehen
            Class<?> userType = org.springframework.aop.support.AopUtils.getTargetClass(bean);
            if (userType == null)
                userType = type;

            for (Method method : userType.getDeclaredMethods()) {
                McpTool ann = method.getAnnotation(McpTool.class);
                if (ann == null)
                    continue;

                String name = ann.name();
                String description = ann.description();

                method.setAccessible(true);
                RegisteredTool rt = new RegisteredTool(name, description, bean, method);
                tools.put(name, rt);

                log.info("Registered MCP tool: {} -> {}#{}", name, userType.getSimpleName(), method.getName());
            }
        }

        log.info("Total MCP tools discovered: {}", tools.size());
    }

    @Override
    public PlannerOutput.Result execute(String capabilityId, Map<String, Object> args) {
        RegisteredTool rt = tools.get(capabilityId);
        if (rt == null) {
            log.warn("Unknown MCP tool capabilityId={}", capabilityId);
            return new PlannerOutput.Result("error", List.of(), "Unknown capabilityId: " + capabilityId);
        }

        try {
            Object result;

            // Erwartung: Methoden-Signatur: XxxResult method(Map<String,Object> args)
            if (rt.method.getParameterCount() == 0) {
                result = rt.method.invoke(rt.bean);
            } else {
                result = rt.method.invoke(rt.bean, args);
            }

            if (result == null) {
                return new PlannerOutput.Result("error", List.of(), "Tool returned null: " + capabilityId);
            }

            // Already a PlannerOutput.Result
            if (result instanceof PlannerOutput.Result por) {
                return por;
            }

            // Implements ToolResult
            if (result instanceof ToolResult tr) {
                // Tool-Ergebnis-Normalisierung: vereinheitlicht Status/Items/Message für den Orchestrator
                return new PlannerOutput.Result(tr.status(), tr.items(), tr.message());
            }

            // Fallback: nicht unterstützter Typ
            return new PlannerOutput.Result("error", List.of(),
                    "Unsupported tool result type: " + result.getClass().getName());

        } catch (Exception e) {
            log.error("Error executing MCP tool {}: {}", capabilityId, e.getMessage(), e);
            return new PlannerOutput.Result("error", List.of(), "Exception in tool: " + e.getMessage());
        }
    }

    @Override
    public Map<String, ToolDescriptor> listTools() {
        Map<String, ToolDescriptor> map = new LinkedHashMap<>();
        tools.forEach((name, rt) -> map.put(name,
                new ToolDescriptor(name, rt.description, rt.bean.getClass(), rt.method.getName())));
        return map;
    }

    private record RegisteredTool(String name, String description, Object bean, Method method) {
    }
}

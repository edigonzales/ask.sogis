package ch.so.agi.ask.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import ch.so.agi.ask.model.McpToolCapability;
import ch.so.agi.ask.model.PlannerOutput;
import ch.so.agi.ask.mcp.McpToolArgSchema;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Spring-basierte Implementierung des {@link ToolRegistry}, die @McpTool-
 * Beans findet, für den MCP-Client registriert und deren Rückgaben in das im
 * README beschriebene PlannerResult-Schema (Status, Items, Message) überführt.
 */
@Component
public class SpringMcpToolRegistry implements ToolRegistry, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(SpringMcpToolRegistry.class);

    private final Map<McpToolCapability, RegisteredTool> tools = new EnumMap<>(McpToolCapability.class);
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
            Class<?> type = applicationContext.getType(beanName);
            if (type == null)
                continue;

            // CGLIB-Proxies: auf die User-Klasse gehen, ohne Bean zu instanziieren
            Class<?> userType = ClassUtils.getUserClass(type);

            for (Method method : userType.getDeclaredMethods()) {
                McpTool ann = method.getAnnotation(McpTool.class);
                if (ann == null)
                    continue;

                String name = ann.name();
                String description = ann.description();

                McpToolCapability capability;
                try {
                    capability = McpToolCapability.fromId(name);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalStateException("Unknown MCP capability declared via @McpTool: " + name, ex);
                }

                method.setAccessible(true);
                List<ToolRegistry.ToolParamDescriptor> params = extractParamDescriptors(method);

                RegisteredTool rt = new RegisteredTool(capability, description, beanName, method, userType, params);
                tools.put(capability, rt);

                log.info("Registered MCP tool: {} -> {}#{}", capability.id(), userType.getSimpleName(), method.getName());
            }
        }

        log.info("Total MCP tools discovered: {}", tools.size());
    }

    @Override
    public PlannerOutput.Result execute(McpToolCapability capabilityId, Map<String, Object> args) {
        RegisteredTool rt = tools.get(capabilityId);
        if (rt == null) {
            log.warn("Unknown MCP tool capabilityId={}", capabilityId);
            return new PlannerOutput.Result("error", List.of(), "Unknown capabilityId: " + capabilityId);
        }

        try {
            Object bean = applicationContext.getBean(rt.beanName());
            Method invocableMethod = resolveInvocableMethod(bean, rt);

            Object result;

            // Erwartung: Methoden-Signatur: XxxResult method(Map<String,Object> args)
            if (invocableMethod.getParameterCount() == 0) {
                result = invocableMethod.invoke(bean);
            } else {
                result = invocableMethod.invoke(bean, args);
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
    public Map<McpToolCapability, ToolDescriptor> listTools() {
        Map<McpToolCapability, ToolDescriptor> map = new EnumMap<>(McpToolCapability.class);
        tools.forEach((capability, rt) -> map.put(capability,
                new ToolDescriptor(capability, rt.description(), rt.userType(), rt.method().getName(), rt.params())));
        return map;
    }

    private List<ToolRegistry.ToolParamDescriptor> extractParamDescriptors(Method method) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return List.of();
        }

        List<ToolRegistry.ToolParamDescriptor> descriptors = new ArrayList<>();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            var annotation = parameter.getAnnotation(McpToolParam.class);
            var schemaAnnotation = parameter.getAnnotation(McpToolArgSchema.class);

            String name = parameter.isNamePresent() ? parameter.getName() : "param" + i;
            String description = annotation != null ? annotation.description() : "";
            boolean required = annotation != null && annotation.required();
            String type = resolveParameterType(parameter);
            String schema = schemaAnnotation != null ? schemaAnnotation.value() : null;

            descriptors.add(new ToolRegistry.ToolParamDescriptor(name, description, required, type, schema));
        }

        return descriptors;
    }

    private String resolveParameterType(Parameter parameter) {
        Type parameterizedType = parameter.getParameterizedType();
        if (parameterizedType instanceof ParameterizedType pt) {
            String raw = ((Class<?>) pt.getRawType()).getSimpleName();
            String args = Arrays.stream(pt.getActualTypeArguments())
                    .map(this::simpleTypeName)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining(", "));
            return args.isBlank() ? raw : raw + "<" + args + ">";
        }
        return parameter.getType().getSimpleName();
    }

    private String simpleTypeName(Type type) {
        if (type instanceof Class<?> cls) {
            return cls.getSimpleName();
        }
        String typeName = type.getTypeName();
        int lastDot = typeName.lastIndexOf('.');
        return lastDot >= 0 ? typeName.substring(lastDot + 1) : typeName;
    }

    private Method resolveInvocableMethod(Object bean, RegisteredTool rt) {
        Method method = rt.method();
        if (!method.getDeclaringClass().isInstance(bean)) {
            Method candidate = ReflectionUtils.findMethod(bean.getClass(), method.getName(), method.getParameterTypes());
            if (candidate != null) {
                candidate.setAccessible(true);
                method = candidate;
            }
        }
        return method;
    }

    private record RegisteredTool(McpToolCapability capability, String description, String beanName, Method method,
            Class<?> userType, List<ToolRegistry.ToolParamDescriptor> params) {
    }
}

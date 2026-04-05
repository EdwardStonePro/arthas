package com.taobao.arthas.core.advisor;

import java.arthas.SpyAPI;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.arthas.deps.org.slf4j.Logger;
import com.alibaba.arthas.deps.org.slf4j.LoggerFactory;
import com.alibaba.bytekit.asm.MethodProcessor;
import com.alibaba.bytekit.asm.interceptor.InterceptorProcessor;
import com.alibaba.bytekit.asm.interceptor.parser.DefaultInterceptorClassParser;
import com.alibaba.bytekit.asm.location.Location;
import com.alibaba.bytekit.asm.location.LocationType;
import com.alibaba.bytekit.asm.location.MethodInsnNodeWare;
import com.alibaba.bytekit.asm.location.filter.GroupLocationFilter;
import com.alibaba.bytekit.asm.location.filter.InvokeCheckLocationFilter;
import com.alibaba.bytekit.asm.location.filter.InvokeContainLocationFilter;
import com.alibaba.bytekit.utils.AsmOpUtils;
import com.alibaba.bytekit.utils.AsmUtils;
import com.alibaba.deps.org.objectweb.asm.Opcodes;
import com.alibaba.deps.org.objectweb.asm.Type;
import com.alibaba.deps.org.objectweb.asm.tree.AbstractInsnNode;
import com.alibaba.deps.org.objectweb.asm.tree.ClassNode;
import com.alibaba.deps.org.objectweb.asm.tree.MethodInsnNode;
import com.alibaba.deps.org.objectweb.asm.tree.MethodNode;
import com.taobao.arthas.core.advisor.SpyInterceptors.MethodEnterInterceptor;
import com.taobao.arthas.core.advisor.SpyInterceptors.MethodExceptionInterceptor;
import com.taobao.arthas.core.advisor.SpyInterceptors.MethodExitInterceptor;
import com.taobao.arthas.core.advisor.SpyInterceptors.TraceAfterInvokeInterceptor;
import com.taobao.arthas.core.advisor.SpyInterceptors.TraceBeforeInvokeInterceptor;
import com.taobao.arthas.core.advisor.SpyInterceptors.TraceExcludeJDKAfterInvokeInterceptor;
import com.taobao.arthas.core.advisor.SpyInterceptors.TraceExcludeJDKBeforeInvokeInterceptor;
import com.taobao.arthas.core.advisor.SpyInterceptors.TraceExcludeJDKInvokeExceptionInterceptor;
import com.taobao.arthas.core.advisor.SpyInterceptors.TraceInvokeExceptionInterceptor;
import com.taobao.arthas.core.util.ArthasCheckUtils;
import com.taobao.arthas.core.util.affect.EnhancerAffect;
import com.taobao.arthas.core.util.matcher.Matcher;

/**
 * Instruments the methods of a class with SpyAPI interception points.
 *
 * Extracted from {@link Enhancer} to separate bytecode manipulation concerns
 * from class discovery and filtering concerns.
 *
 * Builds the interceptor chain according to the tracing mode, selects eligible
 * methods, and injects enter/exit/exception hooks into each of them.
 */
public class MethodInstrumentor {

    private static final Logger logger = LoggerFactory.getLogger(MethodInstrumentor.class);

    private final boolean isTracing;
    private final boolean skipJDKTrace;
    private final AdviceListener listener;
    private final EnhancerAffect affect;
    private final Matcher methodNameMatcher;

    public MethodInstrumentor(boolean isTracing, boolean skipJDKTrace, AdviceListener listener,
            EnhancerAffect affect, Matcher methodNameMatcher) {
        this.isTracing = isTracing;
        this.skipJDKTrace = skipJDKTrace;
        this.listener = listener;
        this.affect = affect;
        this.methodNameMatcher = methodNameMatcher;
    }

    /**
     * Instruments all eligible methods of the given class node.
     */
    public void instrumentMatchingMethods(ClassNode classNode, ClassLoader inClassLoader, String className) {
        final List<InterceptorProcessor> interceptorProcessors = buildInterceptorProcessors();
        List<MethodNode> matchedMethods = findMatchingMethods(classNode);

        // https://github.com/alibaba/arthas/issues/1690
        if (AsmUtils.isEnhancerByCGLIB(className)) {
            for (MethodNode methodNode : matchedMethods) {
                if (AsmUtils.isConstructor(methodNode)) {
                    AsmUtils.fixConstructorExceptionTable(methodNode);
                }
            }
        }

        GroupLocationFilter groupLocationFilter = buildGroupLocationFilter();
        for (MethodNode methodNode : matchedMethods) {
            processMethodNode(methodNode, classNode, interceptorProcessors, groupLocationFilter, inClassLoader, className);
        }
    }

    private List<MethodNode> findMatchingMethods(ClassNode classNode) {
        List<MethodNode> matchedMethods = new ArrayList<MethodNode>();
        for (MethodNode methodNode : classNode.methods) {
            if (!isIgnore(methodNode)) {
                matchedMethods.add(methodNode);
            }
        }
        return matchedMethods;
    }

    private List<InterceptorProcessor> buildInterceptorProcessors() {
        DefaultInterceptorClassParser defaultInterceptorClassParser = new DefaultInterceptorClassParser();
        List<InterceptorProcessor> interceptorProcessors = new ArrayList<InterceptorProcessor>();

        interceptorProcessors.addAll(defaultInterceptorClassParser.parse(MethodEnterInterceptor.class));
        interceptorProcessors.addAll(defaultInterceptorClassParser.parse(MethodExitInterceptor.class));
        interceptorProcessors.addAll(defaultInterceptorClassParser.parse(MethodExceptionInterceptor.class));

        if (this.isTracing) {
            if (!this.skipJDKTrace) {
                interceptorProcessors.addAll(defaultInterceptorClassParser.parse(TraceBeforeInvokeInterceptor.class));
                interceptorProcessors.addAll(defaultInterceptorClassParser.parse(TraceAfterInvokeInterceptor.class));
                interceptorProcessors.addAll(defaultInterceptorClassParser.parse(TraceInvokeExceptionInterceptor.class));
            } else {
                interceptorProcessors.addAll(defaultInterceptorClassParser.parse(TraceExcludeJDKBeforeInvokeInterceptor.class));
                interceptorProcessors.addAll(defaultInterceptorClassParser.parse(TraceExcludeJDKAfterInvokeInterceptor.class));
                interceptorProcessors.addAll(defaultInterceptorClassParser.parse(TraceExcludeJDKInvokeExceptionInterceptor.class));
            }
        }
        return interceptorProcessors;
    }

    private static GroupLocationFilter buildGroupLocationFilter() {
        GroupLocationFilter groupLocationFilter = new GroupLocationFilter();
        groupLocationFilter.addFilter(new InvokeContainLocationFilter(Type.getInternalName(SpyAPI.class), "atEnter", LocationType.ENTER));
        groupLocationFilter.addFilter(new InvokeContainLocationFilter(Type.getInternalName(SpyAPI.class), "atExit", LocationType.EXIT));
        groupLocationFilter.addFilter(new InvokeContainLocationFilter(Type.getInternalName(SpyAPI.class), "atExceptionExit", LocationType.EXCEPTION_EXIT));
        groupLocationFilter.addFilter(new InvokeCheckLocationFilter(Type.getInternalName(SpyAPI.class), "atBeforeInvoke", LocationType.INVOKE));
        groupLocationFilter.addFilter(new InvokeCheckLocationFilter(Type.getInternalName(SpyAPI.class), "atInvokeException", LocationType.INVOKE_COMPLETED));
        groupLocationFilter.addFilter(new InvokeCheckLocationFilter(Type.getInternalName(SpyAPI.class), "atInvokeException", LocationType.INVOKE_EXCEPTION_EXIT));
        return groupLocationFilter;
    }

    private void processMethodNode(MethodNode methodNode, ClassNode classNode,
            List<InterceptorProcessor> interceptorProcessors, GroupLocationFilter groupLocationFilter,
            ClassLoader inClassLoader, String className) {
        if (AsmUtils.isNative(methodNode)) {
            logger.info("ignore native method: {}",
                    AsmUtils.methodDeclaration(Type.getObjectType(classNode.name), methodNode));
            return;
        }
        // 先查找是否有 atBeforeInvoke 函数，如果有，则说明已经有trace了，则直接不再尝试增强，直接插入 listener
        if (AsmUtils.containsMethodInsnNode(methodNode, Type.getInternalName(SpyAPI.class), "atBeforeInvoke")) {
            for (AbstractInsnNode insnNode = methodNode.instructions.getFirst(); insnNode != null; insnNode = insnNode.getNext()) {
                if (insnNode instanceof MethodInsnNode) {
                    final MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                    if (this.skipJDKTrace && methodInsnNode.owner.startsWith("java/")) {
                        continue;
                    }
                    // 原始类型的box类型相关的都跳过
                    if (AsmOpUtils.isBoxType(Type.getObjectType(methodInsnNode.owner))) {
                        continue;
                    }
                    AdviceListenerManager.registerTraceAdviceListener(inClassLoader, className,
                            methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc, listener);
                }
            }
        } else {
            MethodProcessor methodProcessor = new MethodProcessor(classNode, methodNode, groupLocationFilter);
            for (InterceptorProcessor interceptor : interceptorProcessors) {
                try {
                    List<Location> locations = interceptor.process(methodProcessor);
                    for (Location location : locations) {
                        if (location instanceof MethodInsnNodeWare) {
                            MethodInsnNodeWare methodInsnNodeWare = (MethodInsnNodeWare) location;
                            MethodInsnNode methodInsnNode = methodInsnNodeWare.methodInsnNode();
                            AdviceListenerManager.registerTraceAdviceListener(inClassLoader, className,
                                    methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc, listener);
                        }
                    }
                } catch (Throwable e) {
                    logger.error("enhancer error, class: {}, method: {}, interceptor: {}", classNode.name, methodNode.name, interceptor.getClass().getName(), e);
                }
            }
        }
        // enter/exit 总是要插入 listener
        AdviceListenerManager.registerAdviceListener(inClassLoader, className, methodNode.name, methodNode.desc, listener);
        affect.addMethodAndCount(inClassLoader, className, methodNode.name, methodNode.desc);
    }

    private boolean isIgnore(MethodNode methodNode) {
        return null == methodNode
                || isAbstract(methodNode.access)
                || !methodNameMatcher.matching(methodNode.name)
                || ArthasCheckUtils.isEquals(methodNode.name, "<clinit>");
    }

    private static boolean isAbstract(int access) {
        return (Opcodes.ACC_ABSTRACT & access) == Opcodes.ACC_ABSTRACT;
    }
}

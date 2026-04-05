package com.taobao.arthas.core.advisor;

import java.arthas.SpyAPI;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.Mockito;

import com.alibaba.bytekit.utils.AsmUtils;
import com.alibaba.bytekit.utils.Decompiler;
import com.alibaba.deps.org.objectweb.asm.Type;
import com.alibaba.deps.org.objectweb.asm.tree.ClassNode;
import com.alibaba.deps.org.objectweb.asm.tree.MethodNode;
import com.taobao.arthas.core.bytecode.TestHelper;
import com.taobao.arthas.core.server.ArthasBootstrap;
import com.taobao.arthas.core.util.ClassLoaderUtils;
import com.taobao.arthas.core.util.matcher.EqualsMatcher;

import demo.MathGame;
import net.bytebuddy.agent.ByteBuddyAgent;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 
 * @author hengyunabc 2020-05-19
 *
 */
public class EnhancerTest {

    @Test
    public void test() throws Throwable {
        Instrumentation instrumentation = ByteBuddyAgent.install();

        TestHelper.appendSpyJar(instrumentation);

        ArthasBootstrap.getInstance(instrumentation, "ip=127.0.0.1");

        AdviceListener listener = Mockito.mock(AdviceListener.class);

        EqualsMatcher<String> methodNameMatcher = new EqualsMatcher<String>("print");
        EqualsMatcher<String> classNameMatcher = new EqualsMatcher<String>(MathGame.class.getName());

        Enhancer enhancer = new Enhancer(listener, true, false, classNameMatcher, null, methodNameMatcher);

        ClassLoader inClassLoader = MathGame.class.getClassLoader();
        String className = MathGame.class.getName();
        Class<?> classBeingRedefined = MathGame.class;

        ClassNode classNode = AsmUtils.loadClass(MathGame.class);

        byte[] classfileBuffer = AsmUtils.toBytes(classNode);

        byte[] result = enhancer.transform(inClassLoader, className, classBeingRedefined, null, classfileBuffer);

        ClassNode resultClassNode1 = AsmUtils.toClassNode(result);

//        FileUtils.writeByteArrayToFile(new File("/tmp/MathGame1.class"), result);

        result = enhancer.transform(inClassLoader, className, classBeingRedefined, null, result);

        ClassNode resultClassNode2 = AsmUtils.toClassNode(result);

//        FileUtils.writeByteArrayToFile(new File("/tmp/MathGame2.class"), result);

        MethodNode resultMethodNode1 = AsmUtils.findMethods(resultClassNode1.methods, "print").get(0);
        MethodNode resultMethodNode2 = AsmUtils.findMethods(resultClassNode2.methods, "print").get(0);

        Assertions
                .assertThat(AsmUtils
                        .findMethodInsnNode(resultMethodNode1, Type.getInternalName(SpyAPI.class), "atEnter").size())
                .isEqualTo(AsmUtils.findMethodInsnNode(resultMethodNode2, Type.getInternalName(SpyAPI.class), "atEnter")
                        .size());

        Assertions.assertThat(AsmUtils
                .findMethodInsnNode(resultMethodNode1, Type.getInternalName(SpyAPI.class), "atExceptionExit").size())
                .isEqualTo(AsmUtils
                        .findMethodInsnNode(resultMethodNode2, Type.getInternalName(SpyAPI.class), "atExceptionExit")
                        .size());

        Assertions.assertThat(AsmUtils
                .findMethodInsnNode(resultMethodNode1, Type.getInternalName(SpyAPI.class), "atBeforeInvoke").size())
                .isEqualTo(AsmUtils
                        .findMethodInsnNode(resultMethodNode2, Type.getInternalName(SpyAPI.class), "atBeforeInvoke")
                        .size());
        Assertions.assertThat(AsmUtils
                .findMethodInsnNode(resultMethodNode1, Type.getInternalName(SpyAPI.class), "atInvokeException").size())
                .isEqualTo(AsmUtils
                        .findMethodInsnNode(resultMethodNode2, Type.getInternalName(SpyAPI.class), "atInvokeException")
                        .size());

        String string = Decompiler.decompile(result);

        System.err.println(string);
    }

    @Test
    public void testEnhanceWithClassLoaderHash() throws Throwable {
        Instrumentation instrumentation = ByteBuddyAgent.install();
        TestHelper.appendSpyJar(instrumentation);
        ArthasBootstrap.getInstance(instrumentation, "ip=127.0.0.1");

        URL codeSource = MathGame.class.getProtectionDomain().getCodeSource().getLocation();
        URLClassLoader anotherClassLoader = new URLClassLoader(new URL[] { codeSource }, null);
        try {
            Class<?> anotherMathGame = Class.forName(MathGame.class.getName(), true, anotherClassLoader);
            Assertions.assertThat(anotherMathGame.getClassLoader()).isNotSameAs(MathGame.class.getClassLoader());

            AdviceListener listener = Mockito.mock(AdviceListener.class);
            EqualsMatcher<String> methodNameMatcher = new EqualsMatcher<String>("print");
            EqualsMatcher<String> classNameMatcher = new EqualsMatcher<String>(MathGame.class.getName());

            // Enhancer 会过滤与自身 ClassLoader 相同的类（认为是 Arthas 自身加载的类）。
            // 这里用另一个 ClassLoader 加载一份同名类，并用 classloader hash 精确指定只增强这一份。
            String targetClassLoaderHash = Integer.toHexString(anotherClassLoader.hashCode());
            Enhancer enhancer = new Enhancer(listener, false, false, classNameMatcher, null, methodNameMatcher, false,
                    targetClassLoaderHash);

            com.taobao.arthas.core.util.affect.EnhancerAffect affect = enhancer.enhance(instrumentation, 50);

            String expectedMethodPrefix = ClassLoaderUtils.classLoaderHash(anotherClassLoader) + "|"
                    + MathGame.class.getName() + "#print|";
            String nonTargetMethodPrefix = ClassLoaderUtils.classLoaderHash(MathGame.class.getClassLoader()) + "|" + MathGame.class.getName()
                    + "#print|";

            Assertions.assertThat(affect.cCnt()).isEqualTo(1);
            Assertions.assertThat(affect.mCnt()).isEqualTo(1);
            Assertions.assertThat(affect.getMethods()).hasSize(1);
            Assertions.assertThat(affect.getMethods()).allMatch(m -> m.startsWith(expectedMethodPrefix));
            Assertions.assertThat(affect.getMethods()).noneMatch(m -> m.startsWith(nonTargetMethodPrefix));
        } finally {
            anotherClassLoader.close();
        }
    }

    @org.junit.jupiter.api.Test
    public void transformReturnsNullWhenClassLoaderCannotLoadSpyAPI() throws Throwable {
        Instrumentation instrumentation = ByteBuddyAgent.install();
        TestHelper.appendSpyJar(instrumentation);
        ArthasBootstrap.getInstance(instrumentation, "ip=127.0.0.1");

        AdviceListener listener = Mockito.mock(AdviceListener.class);
        EqualsMatcher<String> methodNameMatcher = new EqualsMatcher<String>("print");
        EqualsMatcher<String> classNameMatcher = new EqualsMatcher<String>(MathGame.class.getName());

        Enhancer enhancer = new Enhancer(listener, false, false, classNameMatcher, null, methodNameMatcher);

        // Un classloader qui échoue à charger SpyAPI, simulant une isolation de classloader
        ClassLoader spyAPIBlockingLoader = new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(SpyAPI.class.getName())) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };

        ClassNode classNode = AsmUtils.loadClass(MathGame.class);
        byte[] classfileBuffer = AsmUtils.toBytes(classNode);

        byte[] result = enhancer.transform(spyAPIBlockingLoader, MathGame.class.getName().replace('.', '/'),
                MathGame.class, null, classfileBuffer);

        assertNull(result);
    }

    @org.junit.jupiter.api.Test
    public void transformWithTracingAddsAtBeforeInvokeInstructions() throws Throwable {
        Instrumentation instrumentation = ByteBuddyAgent.install();
        TestHelper.appendSpyJar(instrumentation);
        ArthasBootstrap.getInstance(instrumentation, "ip=127.0.0.1");

        AdviceListener listener = Mockito.mock(AdviceListener.class);
        EqualsMatcher<String> methodNameMatcher = new EqualsMatcher<String>("print");
        EqualsMatcher<String> classNameMatcher = new EqualsMatcher<String>(MathGame.class.getName());

        // isTracing = true : les intercepteurs de trace (atBeforeInvoke) doivent être injectés
        Enhancer enhancer = new Enhancer(listener, true, false, classNameMatcher, null, methodNameMatcher);

        ClassNode classNode = AsmUtils.loadClass(MathGame.class);
        byte[] classfileBuffer = AsmUtils.toBytes(classNode);

        byte[] result = enhancer.transform(MathGame.class.getClassLoader(),
                MathGame.class.getName().replace('.', '/'), MathGame.class, null, classfileBuffer);

        assertNotNull(result);
        ClassNode resultClassNode1 = AsmUtils.toClassNode(result);
        MethodNode printMethod = AsmUtils.findMethods(resultClassNode1.methods, "print").get(0);
        assertTrue(AsmUtils.findMethodInsnNode(printMethod, Type.getInternalName(SpyAPI.class), "atBeforeInvoke").size() > 0);
    }

    @org.junit.jupiter.api.Test
    public void transformWithoutTracingHasNoAtBeforeInvokeInstructions() throws Throwable {
        Instrumentation instrumentation = ByteBuddyAgent.install();
        TestHelper.appendSpyJar(instrumentation);
        ArthasBootstrap.getInstance(instrumentation, "ip=127.0.0.1");

        AdviceListener listener = Mockito.mock(AdviceListener.class);
        EqualsMatcher<String> methodNameMatcher = new EqualsMatcher<String>("print");
        EqualsMatcher<String> classNameMatcher = new EqualsMatcher<String>(MathGame.class.getName());

        // isTracing = false : seulement atEnter/atExit, pas d'atBeforeInvoke
        Enhancer enhancer = new Enhancer(listener, false, false, classNameMatcher, null, methodNameMatcher);

        ClassNode classNode = AsmUtils.loadClass(MathGame.class);
        byte[] classfileBuffer = AsmUtils.toBytes(classNode);

        byte[] result = enhancer.transform(MathGame.class.getClassLoader(),
                MathGame.class.getName().replace('.', '/'), MathGame.class, null, classfileBuffer);

        assertNotNull(result);
        ClassNode resultClassNode2 = AsmUtils.toClassNode(result);
        MethodNode printMethod = AsmUtils.findMethods(resultClassNode2.methods, "print").get(0);
        assertTrue(AsmUtils.findMethodInsnNode(printMethod, Type.getInternalName(SpyAPI.class), "atEnter").size() > 0);
        assertEquals(0, AsmUtils.findMethodInsnNode(printMethod, Type.getInternalName(SpyAPI.class), "atBeforeInvoke").size());
    }

}

package com.taobao.arthas.core.advisor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.taobao.arthas.common.Pair;
import com.taobao.arthas.core.GlobalOptions;
import com.taobao.arthas.core.util.ClassUtils;
import com.taobao.arthas.core.util.matcher.Matcher;

import static com.taobao.arthas.core.util.ArthasCheckUtils.isEquals;

/**
 * Determines which classes are eligible for bytecode enhancement.
 *
 * Extracted from {@link Enhancer} to separate filtering concerns from
 * instrumentation concerns.
 */
public class ClassFilter {

    private final Matcher classNameExcludeMatcher;
    private final String targetClassLoaderHash;
    private final ClassLoader selfClassLoader;

    public ClassFilter(Matcher classNameExcludeMatcher, String targetClassLoaderHash, ClassLoader selfClassLoader) {
        this.classNameExcludeMatcher = classNameExcludeMatcher;
        this.targetClassLoaderHash = targetClassLoaderHash;
        this.selfClassLoader = selfClassLoader;
    }

    /**
     * Removes non-eligible classes from the given set.
     *
     * @return the list of removed classes with the reason for their removal
     */
    public List<Pair<Class<?>, String>> removeNonEligible(Set<Class<?>> classes) {
        List<Pair<Class<?>, String>> removedClasses = new ArrayList<Pair<Class<?>, String>>();
        final Iterator<Class<?>> it = classes.iterator();
        while (it.hasNext()) {
            final Class<?> clazz = it.next();
            boolean removeFlag = false;
            if (null == clazz) {
                removeFlag = true;
            } else if (!isTargetClassLoader(clazz.getClassLoader())) {
                removedClasses.add(new Pair<Class<?>, String>(clazz, "classloader is not matched"));
                removeFlag = true;
            } else if (isSelf(clazz)) {
                removedClasses.add(new Pair<Class<?>, String>(clazz, "class loaded by arthas itself"));
                removeFlag = true;
            } else if (isUnsafeClass(clazz)) {
                removedClasses.add(new Pair<Class<?>, String>(clazz, "class loaded by Bootstrap Classloader, try to execute `options unsafe true`"));
                removeFlag = true;
            } else if (isExclude(clazz)) {
                removedClasses.add(new Pair<Class<?>, String>(clazz, "class is excluded"));
                removeFlag = true;
            } else {
                Pair<Boolean, String> unsupportedResult = isUnsupportedClass(clazz);
                if (unsupportedResult.getFirst()) {
                    removedClasses.add(new Pair<Class<?>, String>(clazz, unsupportedResult.getSecond()));
                    removeFlag = true;
                }
            }
            if (removeFlag) {
                it.remove();
            }
        }
        return removedClasses;
    }

    /**
     * Returns true if the given classloader matches the target classloader hash,
     * or if no target hash was specified.
     */
    public boolean isTargetClassLoader(ClassLoader inClassLoader) {
        if (targetClassLoaderHash == null || targetClassLoaderHash.isEmpty()) {
            return true;
        }
        if (inClassLoader == null) {
            return false;
        }
        return Integer.toHexString(inClassLoader.hashCode()).equalsIgnoreCase(targetClassLoaderHash);
    }

    /**
     * Returns true if the given classloader is the Arthas classloader itself.
     */
    public boolean isSelfClassLoader(ClassLoader classLoader) {
        return isEquals(classLoader, selfClassLoader);
    }

    private boolean isExclude(Class<?> clazz) {
        if (this.classNameExcludeMatcher != null) {
            return classNameExcludeMatcher.matching(clazz.getName());
        }
        return false;
    }

    public boolean isExcludedByName(String classNameDot) {
        return classNameExcludeMatcher != null && classNameExcludeMatcher.matching(classNameDot);
    }

    private boolean isSelf(Class<?> clazz) {
        return null != clazz && isEquals(clazz.getClassLoader(), selfClassLoader);
    }

    private static boolean isUnsafeClass(Class<?> clazz) {
        return !GlobalOptions.isUnsafe && clazz.getClassLoader() == null;
    }

    private static Pair<Boolean, String> isUnsupportedClass(Class<?> clazz) {
        if (ClassUtils.isLambdaClass(clazz)) {
            return new Pair<Boolean, String>(Boolean.TRUE, "class is lambda");
        }
        if (clazz.isInterface() && !GlobalOptions.isSupportDefaultMethod) {
            return new Pair<Boolean, String>(Boolean.TRUE, "class is interface");
        }
        if (clazz.equals(Integer.class)) {
            return new Pair<Boolean, String>(Boolean.TRUE, "class is java.lang.Integer");
        }
        if (clazz.equals(Class.class)) {
            return new Pair<Boolean, String>(Boolean.TRUE, "class is java.lang.Class");
        }
        if (clazz.equals(Method.class)) {
            return new Pair<Boolean, String>(Boolean.TRUE, "class is java.lang.Method");
        }
        if (clazz.isArray()) {
            return new Pair<Boolean, String>(Boolean.TRUE, "class is array");
        }
        return new Pair<Boolean, String>(Boolean.FALSE, "");
    }
}

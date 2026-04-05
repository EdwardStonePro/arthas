package com.taobao.arthas.core.env;

import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.taobao.arthas.core.util.StringUtils;

public class DefaultConversionService implements ConfigurableConversionService {

    private static ConcurrentHashMap<ConvertiblePair, Converter> converters = new ConcurrentHashMap<ConvertiblePair, Converter>();

    public DefaultConversionService() {
        addDefaultConverter();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void addDefaultConverter() {
        converters.put(new ConvertiblePair(String.class, Integer.class),
                (Converter<String, Integer>) (source, targetType) -> Integer.parseInt(source));
        converters.put(new ConvertiblePair(String.class, Long.class),
                (Converter<String, Long>) (source, targetType) -> Long.parseLong(source));
        converters.put(new ConvertiblePair(String.class, Boolean.class), new StringToBooleanConverter());
        converters.put(new ConvertiblePair(String.class, InetAddress.class), new StringToInetAddressConverter());
        converters.put(new ConvertiblePair(String.class, Enum.class),
                (Converter<String, Enum>) (source, targetType) -> Enum.valueOf(targetType, source));
        converters.put(new ConvertiblePair(String.class, Arrays.class), new StringToArrayConverter(this));
    }

    @Override
    public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
        if (sourceType == targetType) {
            return true;
        }
        if (targetType.isPrimitive()) {
            targetType = objectiveClass(targetType);
        }
        if (converters.containsKey(new ConvertiblePair(sourceType, targetType))) {
            return true;
        }
        if (targetType.isEnum()) {
            if (converters.containsKey(new ConvertiblePair(sourceType, Enum.class))) {
                return true;
            }
        }
        if (targetType.isArray()) {
            return true;
        }
        return false;
    }

    @Override
    public <T> T convert(Object source, Class<T> targetType) {
        if (targetType.isPrimitive()) {
            targetType = (Class<T>) objectiveClass(targetType);
        }
        Converter converter = converters.get(new ConvertiblePair(source.getClass(), targetType));
        if (converter == null && targetType.isArray()) {
            converter = converters.get(new ConvertiblePair(source.getClass(), Arrays.class));
        }
        if (converter == null && targetType.isEnum()) {
            converter = converters.get(new ConvertiblePair(source.getClass(), Enum.class));
        }
        if (converter != null) {
            return (T) converter.convert(source, targetType);
        }
        return (T) source;
    }

    public static <C> Class<C[]> arrayClass(Class<C> klass) {
        return (Class<C[]>) Array.newInstance(klass, 0).getClass();
    }

    public static Class<?> objectiveClass(Class<?> klass) {
        Class<?> component = klass.getComponentType();
        if (component != null) {
            if (component.isPrimitive() || component.isArray())
                return arrayClass(objectiveClass(component));
        } else if (klass.isPrimitive()) {
            if (klass == char.class)    return Character.class;
            if (klass == int.class)     return Integer.class;
            if (klass == boolean.class) return Boolean.class;
            if (klass == byte.class)    return Byte.class;
            if (klass == double.class)  return Double.class;
            if (klass == float.class)   return Float.class;
            if (klass == long.class)    return Long.class;
            if (klass == short.class)   return Short.class;
        }
        return klass;
    }

    private static final class StringToBooleanConverter implements Converter<String, Boolean> {

        private static final Set<String> trueValues = new HashSet<String>(4);
        private static final Set<String> falseValues = new HashSet<String>(4);

        static {
            trueValues.add("true");
            trueValues.add("on");
            trueValues.add("yes");
            trueValues.add("1");

            falseValues.add("false");
            falseValues.add("off");
            falseValues.add("no");
            falseValues.add("0");
        }

        @Override
        public Boolean convert(String source, Class<Boolean> targetType) {
            String value = source.trim();
            if ("".equals(value)) {
                return null;
            }
            value = value.toLowerCase();
            if (trueValues.contains(value)) {
                return Boolean.TRUE;
            } else if (falseValues.contains(value)) {
                return Boolean.FALSE;
            } else {
                throw new IllegalArgumentException("Invalid boolean value '" + source + "'");
            }
        }
    }

    private static final class StringToInetAddressConverter implements Converter<String, InetAddress> {

        @Override
        public InetAddress convert(String source, Class<InetAddress> targetType) {
            try {
                return InetAddress.getByName(source);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid InetAddress value '" + source + "'", e);
            }
        }
    }

    private static final class StringToArrayConverter<T> implements Converter<String, T[]> {

        private final ConversionService conversionService;

        StringToArrayConverter(ConversionService conversionService) {
            this.conversionService = conversionService;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T[] convert(String source, Class<T[]> targetType) {
            String[] strings = StringUtils.tokenizeToStringArray(source, ",");
            T[] values = (T[]) Array.newInstance(targetType.getComponentType(), strings.length);
            for (int i = 0; i < strings.length; ++i) {
                values[i] = (T) conversionService.convert(strings[i], targetType.getComponentType());
            }
            return values;
        }
    }
}

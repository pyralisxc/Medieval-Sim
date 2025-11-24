package medievalsim.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Utility methods for reflection operations used across Medieval Sim.
 * Consolidates common reflection patterns to reduce code duplication.
 */
public final class ReflectionUtils {
    
    private ReflectionUtils() {
        // Utility class - no instantiation
    }
    
    /**
     * Scan all public static fields in a class that match the given filter.
     * 
     * @param clazz The class to scan
     * @param filter Predicate to filter which fields to include (null = all public static)
     * @return List of matching fields
     */
    public static List<Field> scanPublicStaticFields(Class<?> clazz, Predicate<Field> filter) {
        List<Field> result = new ArrayList<>();
        
        if (clazz == null) {
            return result;
        }
        
        try {
            Field[] fields = clazz.getDeclaredFields();
            
            for (Field field : fields) {
                // Check if field is public and static
                int modifiers = field.getModifiers();
                if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
                    continue;
                }
                
                // Apply custom filter if provided
                if (filter != null && !filter.test(field)) {
                    continue;
                }
                
                result.add(field);
            }
            
        } catch (Exception e) {
            ModLogger.error("Failed to scan fields in class %s: %s", clazz.getName(), e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Get a property value from an annotation using reflection.
     * 
     * @param annotation The annotation instance
     * @param propertyName The name of the property/method to invoke
     * @param returnType The expected return type
     * @return The property value, or null if not found or error
     */
    public static <T> T getAnnotationProperty(Annotation annotation, String propertyName, Class<T> returnType) {
        if (annotation == null || propertyName == null || returnType == null) {
            return null;
        }
        
        try {
            Method method = annotation.annotationType().getMethod(propertyName);
            Object value = method.invoke(annotation);
            
            if (returnType.isInstance(value)) {
                return returnType.cast(value);
            }
            
        } catch (Exception e) {
            ModLogger.debug("Failed to get annotation property %s: %s", propertyName, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Set a field value using reflection, handling accessibility.
     * 
     * @param field The field to set
     * @param instance The object instance (null for static fields)
     * @param value The value to set
     * @return true if successful, false otherwise
     */
    public static boolean setFieldValue(Field field, Object instance, Object value) {
        if (field == null) {
            return false;
        }
        
        try {
            boolean wasAccessible = field.canAccess(instance);
            
            if (!wasAccessible) {
                field.setAccessible(true);
            }
            
            field.set(instance, value);
            
            if (!wasAccessible) {
                field.setAccessible(false);
            }
            
            return true;
            
        } catch (Exception e) {
            ModLogger.error("Failed to set field %s: %s", field.getName(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Get a field value using reflection, handling accessibility.
     * 
     * @param field The field to get
     * @param instance The object instance (null for static fields)
     * @return The field value, or null if error
     */
    public static Object getFieldValue(Field field, Object instance) {
        if (field == null) {
            return null;
        }
        
        try {
            boolean wasAccessible = field.canAccess(instance);
            
            if (!wasAccessible) {
                field.setAccessible(true);
            }
            
            Object value = field.get(instance);
            
            if (!wasAccessible) {
                field.setAccessible(false);
            }
            
            return value;
            
        } catch (Exception e) {
            ModLogger.error("Failed to get field %s: %s", field.getName(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if a field has a specific annotation.
     * 
     * @param field The field to check
     * @param annotationClass The annotation class
     * @return true if field has the annotation
     */
    public static boolean hasAnnotation(Field field, Class<? extends Annotation> annotationClass) {
        return field != null && annotationClass != null && field.isAnnotationPresent(annotationClass);
    }
    
    /**
     * Get an annotation from a field.
     * 
     * @param field The field to check
     * @param annotationClass The annotation class
     * @return The annotation instance, or null if not present
     */
    public static <T extends Annotation> T getAnnotation(Field field, Class<T> annotationClass) {
        if (field == null || annotationClass == null) {
            return null;
        }
        
        return field.getAnnotation(annotationClass);
    }
    
    /**
     * Determine the primitive type or wrapper for a field type.
     * 
     * @param fieldType The field's type
     * @return Simplified type identifier
     */
    public static Class<?> unwrapType(Class<?> fieldType) {
        if (fieldType == null) {
            return null;
        }
        
        // Handle primitive wrappers
        if (fieldType == Integer.class) return int.class;
        if (fieldType == Long.class) return long.class;
        if (fieldType == Float.class) return float.class;
        if (fieldType == Double.class) return double.class;
        if (fieldType == Boolean.class) return boolean.class;
        if (fieldType == Byte.class) return byte.class;
        if (fieldType == Short.class) return short.class;
        if (fieldType == Character.class) return char.class;
        
        return fieldType;
    }
    
    /**
     * Check if a type is numeric (int, long, float, double, or their wrappers).
     * 
     * @param type The type to check
     * @return true if numeric
     */
    public static boolean isNumericType(Class<?> type) {
        if (type == null) {
            return false;
        }
        
        return type == int.class || type == Integer.class
            || type == long.class || type == Long.class
            || type == float.class || type == Float.class
            || type == double.class || type == Double.class;
    }
}
